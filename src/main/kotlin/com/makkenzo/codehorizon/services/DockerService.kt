package com.makkenzo.codehorizon.services

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import com.makkenzo.codehorizon.dtos.DockerExecutionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString


@Service
class DockerService {
    private val logger = LoggerFactory.getLogger(DockerService::class.java)
    private val dockerClient: DockerClient

    private val pythonImageName = "codehorizon-python-runner:latest"
    private val javascriptImageName = "codehorizon-javascript-runner:latest"
    private val baseTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "codehorizon_runner")

    private val maxConcurrentContainers = 5
    private val containerExecutionPermits = Semaphore(maxConcurrentContainers)

    init {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()
        dockerClient = DockerClientImpl.getInstance(config, httpClient)

        if (!Files.exists(baseTempDir)) {
            Files.createDirectories(baseTempDir)
        }

        ensureDockerAvailable()

        buildImageIfNotExists(pythonImageName, "docker/python-runner.Dockerfile")
        buildImageIfNotExists(javascriptImageName, "docker/javascript-runner.Dockerfile")
    }

    private fun ensureDockerAvailable() {
        try {
            dockerClient.pingCmd().exec()
            logger.info("Docker daemon is available.")
        } catch (e: DockerClientException) {
            logger.error("Docker daemon is not available or not responding: ${e.message}", e)
            throw IllegalStateException("Docker daemon is not available. Grading service will be disabled.", e)
        }
    }

    private fun buildImageIfNotExists(imageName: String, dockerfileRelativePath: String) {
        try {
            logger.info("--- Checking/Building Docker image: $imageName ---")
            val existingImage = dockerClient.listImagesCmd().withImageNameFilter(imageName).exec()

            if (existingImage.isNotEmpty()) {
                logger.info("Docker image $imageName found locally (ID: ${existingImage.first().id.take(12)}). Skipping build.")
                logger.info("--- Docker image check for $imageName complete ---")
                return
            }

            logger.info("Docker image $imageName NOT FOUND locally. Starting build...")

            val dockerfile = File(dockerfileRelativePath)
            val dockerContextPath = dockerfile.parentFile ?: File(".")

            if (!dockerfile.exists()) {
                logger.error("Dockerfile not found at path: ${dockerfile.absolutePath}")
                throw IllegalStateException("Dockerfile not found for $imageName at ${dockerfile.absolutePath}")
            }
            logger.info("Using Dockerfile: ${dockerfile.absolutePath}, Context: ${dockerContextPath.absolutePath}")


            val buildCallbackLog = object : BuildImageResultCallback() {
                override fun onNext(item: BuildResponseItem?) {
                    item?.stream?.trim()
                        ?.let { logMsg -> if (logMsg.isNotEmpty()) logger.info("Build log ($imageName): $logMsg") }
                    item?.progressDetail?.let { progress ->
                        logger.debug("Build progress ($imageName): current=${progress.current}, total=${progress.total}")
                    }
                    item?.errorDetail?.let { error ->
                        logger.error("Build error detail ($imageName): ${error.code} - ${error.message}")
                    }
                    super.onNext(item)
                }

                override fun onError(throwable: Throwable?) {
                    logger.error("Build process error ($imageName): ", throwable)
                    super.onError(throwable)
                }

                override fun onComplete() {
                    logger.info("BuildImageResultCallback onComplete triggered for $imageName.")
                    super.onComplete()
                }
            }

            try {
                dockerClient.buildImageCmd(dockerfile)
                    .withTags(setOf(imageName))
                    // .withDockerfile(dockerfile) // Dockerfile уже указан в buildImageCmd(File)
                    .withPull(true)
                    .exec(buildCallbackLog)
                    .awaitCompletion(5, TimeUnit.MINUTES)
            } catch (e: Exception) {
                logger.error("Exception during buildImageCmd execution for $imageName: ${e.message}", e)
                throw IllegalStateException("Failed to build Docker image $imageName due to: ${e.message}", e)
            }

            val imagesAfterBuild = dockerClient.listImagesCmd().withImageNameFilter(imageName).exec()
            if (imagesAfterBuild.isEmpty()) {
                logger.error("!!! Docker image $imageName did NOT appear after build attempt !!! Check build logs above.")
                throw IllegalStateException("Docker image $imageName could not be built or found after build attempt.")
            } else {
                logger.info(
                    "Docker image $imageName successfully built/verified (ID: ${
                        imagesAfterBuild.first().id.take(
                            12
                        )
                    })."
                )
            }

            logger.info("--- Docker image build for $imageName complete ---")
        } catch (e: DockerClientException) {
            logger.error("Docker client error during image build/check for $imageName: ${e.message}", e)
            throw IllegalStateException("Docker client error for $imageName: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Critical error during Docker image build/check for $imageName: ${e.message}", e)
            throw IllegalStateException("Failed to ensure Docker image $imageName is available", e)
        }
    }

    fun executeCodeInContainer(
        imageName: String,
        studentCodeContent: String,
        studentCodeFileName: String,
        testRunnerScriptContent: String,
        testRunnerFileName: String,
        testCasesJsonContent: String,
        command: List<String>,
        timeoutSeconds: Long = 10,
        memoryLimitMb: Long = 128,
        cpuQuotaMicroseconds: Long = 50000
    ): DockerExecutionResult {
        var permitAcquired = false
        val runId = UUID.randomUUID().toString()
        val tempRunDir: Path = baseTempDir.resolve(runId)

        try {
            if (!containerExecutionPermits.tryAcquire(timeoutSeconds + 5, TimeUnit.SECONDS)) {
                logger.warn("Timeout acquiring execution permit for Docker container (Run ID: $runId, Image: $imageName).")
                return DockerExecutionResult("", "", -1, 0, "Execution permit not acquired; server busy.")
            }
            permitAcquired = true
            logger.debug("Execution permit acquired for Docker container (Run ID: $runId, Image: $imageName). Available: ${containerExecutionPermits.availablePermits()}")

            Files.createDirectories(tempRunDir)
            val hostPath = tempRunDir.absolutePathString()
            val containerPath = "/usr/src/app/run_dir"

            var containerId: String? = null

            try {
                Files.writeString(
                    tempRunDir.resolve(studentCodeFileName),
                    studentCodeContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                )
                Files.writeString(
                    tempRunDir.resolve(testRunnerFileName),
                    testRunnerScriptContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                )
                Files.writeString(
                    tempRunDir.resolve("test_data.json"),
                    testCasesJsonContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                )

                val hostConfig = HostConfig.newHostConfig()
                    .withMemory(memoryLimitMb * 1024 * 1024)
                    .withCpuQuota(cpuQuotaMicroseconds)
                    .withCpuPeriod(100000L)
                    .withPrivileged(false)
                    .withNetworkMode("none")
                    .withBinds(Bind.parse("$hostPath:$containerPath:ro"))
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(listOf("no-new-privileges"))
                    .withPidsLimit(64L)
                    .withReadonlyRootfs(true)

                val containerResponse: CreateContainerResponse = dockerClient.createContainerCmd(imageName)
                    .withHostConfig(hostConfig)
                    .withWorkingDir(containerPath)
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withUser("appuser")
                    .withEnv("PYTHONUNBUFFERED=1")
                    .exec()

                containerId = containerResponse.id
                logger.info("Container $containerId created for Run ID: $runId (Image: $imageName)")

                val startTime = System.currentTimeMillis()
                dockerClient.startContainerCmd(containerId).exec()
                logger.debug("Container $containerId started for Run ID: $runId")

                val waitCallback = dockerClient.waitContainerCmd(containerId).start()
                val statusCode = try {
                    waitCallback.awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS)
                } catch (e: DockerClientException) {
                    logger.warn("Error waiting for container $containerId (Run ID: $runId): ${e.message}. Attempting to get logs.")
                    if (e.message?.contains("timed out", ignoreCase = true) == true) -2
                    else -3
                } finally {
                    waitCallback.close()
                }

                val executionTimeMs = System.currentTimeMillis() - startTime
                logger.debug("Container $containerId (Run ID: $runId) finished with status code: $statusCode in ${executionTimeMs}ms.")

                val logCallback = LogContainerCallback()
                try {
                    dockerClient.logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTimestamps(false)
                        .exec(logCallback)
                        .awaitCompletion(5, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    logger.error("Error getting logs for container $containerId (Run ID: $runId): ${e.message}", e)
                }

                val errorMsg = when (statusCode) {
                    -1 -> "Container execution status unknown (possibly not started or status retrieval failed)."
                    -2 -> "Execution timed out after $timeoutSeconds seconds."
                    -3 -> "Docker client error during container execution."
                    137 -> "Execution stopped: Out of Memory (OOMKilled) or killed by SIGKILL."
                    139 -> "Execution stopped: Segmentation fault (SIGSEGV)."
                    else -> if (statusCode != 0 && logCallback.stderr.isNotBlank() && logCallback.stdout.isNotBlank()) {
                        "Execution failed with exit code $statusCode."
                    } else if (statusCode != 0 && logCallback.stderr.isBlank() && logCallback.stdout.isBlank()) {
                        "Execution failed with exit code $statusCode without specific error output."
                    } else null
                }

                return DockerExecutionResult(
                    stdout = logCallback.stdout.toString(),
                    stderr = logCallback.stderr.toString(),
                    exitCode = statusCode ?: -1,
                    executionTimeMs = executionTimeMs,
                    error = errorMsg
                )
            } catch (e: Exception) {
                logger.error(
                    "Error during Docker code execution for Run ID $runId (Image: $imageName): ${e.message}",
                    e
                )
                return DockerExecutionResult("", "", -1, 0, "Docker execution failed: ${e.message?.take(200)}")
            } finally {
                containerId?.let { id ->
                    try {
                        dockerClient.removeContainerCmd(id).withForce(true).exec()
                        logger.info("Container $id removed for Run ID: $runId")
                    } catch (e: Exception) {
                        logger.warn("Failed to remove container $id (Run ID: $runId): ${e.message}")
                    }
                }

                try {
                    if (Files.exists(tempRunDir)) {
                        Files.walk(tempRunDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete)
                    }
                } catch (ioe: Exception) {
                    logger.warn("Failed to fully delete temporary directory $tempRunDir for Run ID $runId: ${ioe.message}")
                }
            }
        } catch (e: InterruptedException) {
            logger.warn(
                "Permit acquisition for Docker container (Run ID: $runId, Image: $imageName) was interrupted.",
                e
            )
            Thread.currentThread().interrupt()
            return DockerExecutionResult("", "", -1, 0, "Container execution permit interrupted.")
        } catch (e: Exception) {
            logger.error("Generic error in Docker execution (Run ID: $runId, Image: $imageName): ${e.message}", e)
            return DockerExecutionResult("", "", -1, 0, "Docker execution failed: ${e.message?.take(200)}")
        } finally {
            if (permitAcquired) {
                containerExecutionPermits.release()
                logger.debug("Execution permit released for Docker container (Run ID: $runId, Image: $imageName). Available: ${containerExecutionPermits.availablePermits()}")
            }
        }
    }
}

class LogContainerCallback : ResultCallback<Frame> {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    private var completed = false
    private val lock = Object()
    private var closeable: Closeable? = null

    override fun onStart(closeable: Closeable?) {
        this.closeable = closeable
    }

    override fun onNext(frame: Frame?) {
        frame?.let {
            when (it.streamType) {
                com.github.dockerjava.api.model.StreamType.STDOUT,
                com.github.dockerjava.api.model.StreamType.RAW -> stdout.append(
                    String(
                        it.payload,
                        StandardCharsets.UTF_8
                    )
                )

                com.github.dockerjava.api.model.StreamType.STDERR -> stderr.append(
                    String(
                        it.payload,
                        StandardCharsets.UTF_8
                    )
                )

                else -> {}
            }
        }
    }

    override fun onError(throwable: Throwable?) {
        System.err.println("Error in LogContainerCallback: ${throwable?.message}")
        onComplete()
    }

    override fun onComplete() {
        synchronized(lock) {
            if (!completed) {
                completed = true
                lock.notifyAll()
            }
        }
    }

    override fun close() {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
        onComplete()
    }

    fun awaitCompletion(timeout: Long, unit: TimeUnit): Boolean {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = unit.toMillis(timeout)

        synchronized(lock) {
            while (!completed) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = timeoutMillis - elapsedTime
                if (remainingTime <= 0) {
                    return false
                }
                try {
                    lock.wait(remainingTime)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return true
        }
    }
}