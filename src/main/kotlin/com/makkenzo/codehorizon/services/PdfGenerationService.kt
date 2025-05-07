package com.makkenzo.codehorizon.services

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.font.FontProvider
import com.makkenzo.codehorizon.models.Certificate
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class PdfGenerationService(private val templateEngine: TemplateEngine) {
    private val logger = LoggerFactory.getLogger(PdfGenerationService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
    private val fontProvider: FontProvider = FontProvider()

    init {
        try {
            val liberationSansPath = "fonts/LiberationSans-Regular.ttf"
            val dejavuSansPath = "fonts/DejaVuSans.ttf"

            val liberationResource = ClassPathResource(liberationSansPath)
            if (liberationResource.exists()) {
                val liberationBytes = liberationResource.inputStream.readAllBytes()
                val liberationFontProgram = FontProgramFactory.createFont(liberationBytes, true)
                fontProvider.addFont(liberationFontProgram)
                logger.info("Шрифт Liberation Sans ({}) успешно добавлен в FontProvider.", liberationSansPath)
            } else {
                logger.warn("Шрифт Liberation Sans не найден в classpath: {}", liberationSansPath)
            }

            val dejavuResource = ClassPathResource(dejavuSansPath)
            if (dejavuResource.exists()) {
                val dejavuBytes = dejavuResource.inputStream.readAllBytes()
                val dejavuFontProgram = FontProgramFactory.createFont(dejavuBytes, true)
                fontProvider.addFont(dejavuFontProgram)
                logger.info("Шрифт DejaVu Sans ({}) успешно добавлен в FontProvider.", dejavuSansPath)
            } else {
                logger.warn("Шрифт DejaVu Sans не найден в classpath: {}", dejavuSansPath)
            }

        } catch (e: Exception) {
            logger.error("Ошибка при добавлении шрифтов в FontProvider iText: {}", e.message, e)
        }
        fontProvider.addStandardPdfFonts()
    }


    fun generateCertificatePdf(certificate: Certificate): ByteArray {
        val context = Context()
        context.setVariable("userName", certificate.userName)
        context.setVariable("courseTitle", certificate.courseTitle)
        context.setVariable("completionDate", dateFormatter.format(certificate.completionDate))
        context.setVariable("certificateId", certificate.uniqueCertificateId)
        context.setVariable("instructorName", certificate.instructorName ?: "Инструктор Курса")
        context.setVariable("instructorSignatureUrl", certificate.instructorSignatureUrl)
        context.setVariable(
            "codehorizonSignatureUrl",
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAcIAAAC0CAYAAAAHFCwtAAAgAElEQVR4Xu2dCdRVVfmHX2w5IFFpCqmlaZNZoZaCkU1kaJoDAmGWOaTpIgQFRWmQzEoKSEkLtZzKkcRSK1LQJkHFJM0INEuxtLRyKBWzVfz/z65Nh+u93z3n3DPv37vWXd+F75w9PHt/53f23u9+d781/28mEwEREAEREIFACfSTEAba8qq2CIiACIiAIyAhVEcQAREQAREImoCEMOjmV+VFQAREQAQkhOoDIiACIiACQROQEAbd/Kq8CIiACIiAhFB9QAREQAREIGgCEsKgm1+VFwEREAERkBCqD4iACIiACARNQEIYdPOr8iIgAiIgAhJC9QEREAEREIGgCUgIg25+VV4EREAEREBCqD4gAiIgAiIQNAEJYdDNr8qLgAiIgAhICNUHREAEREAEgiYgIQy6+VV5ERABERABCaH6gAiIgAiIQNAEJIRBN78qLwIiIAIiICFUHxABERABEQiagIQw6OZX5UVABERABCSE6gMiIAIiIAJBE5AQBt38qrwIiIAIiICEUH1ABERABEQgaAISwqCbX5UXAREQARGQEKoPiIAIiIAIBE1AQhh086vyIiACIiACEkL1AREQAREQgaAJSAiDbn5VXgREQAREQEKoPiACIiACIhA0AQlh0M2vyouACIiACEgI1QdEQAREQASCJiAhDLr5VXkREAEREAEJofqACIiACIhA0AQkhEE3vyovAiIgAiIgIVQfEAEREAERCJqAhDDo5lflRUAEREAEJITqAyIgAiIgAkETkBAG3fyqvAiIgAiIgIRQfUAEREAERCBoAhLCoJtflRcBERABEZAQqg+IgAiIgAgETUBCGHTzq/IiIAIiIAISQvUBERABERCBoAlICINuflVeBERABERAQqg+IAIiIAIiEDQBCWHQza/Ki4AIiIAISAjVB0RABERABIImICEMuvlVeRGoPoEFCxbY6NGjrX///nb55ZfbyJEjq19olbBWBCSEtWouFVYEwiPwmc98xk499VRX8UGDBtmKFSts0003DQ+EapwbAQlhbmiVsAiIQBYEokJIemPHjrV58+ZlkbTSEAFHQEKojiACIlBpAl4IJ0yY4ATw0Ucftf33399OPvlk22233SpddhWuHgQkhPVoJ5VSBIIl4IVw+vTpNmTIELde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"
        )

        val htmlContent: String = try {
            templateEngine.process("certificate-template", context)
        } catch (e: Exception) {
            logger.error("Ошибка генерации HTML: {}", e.message, e)
            throw RuntimeException("Ошибка генерации HTML для сертификата", e)
        }

        val outputStream = ByteArrayOutputStream()

        try {
            val writer = PdfWriter(outputStream)
            val pdfDocument = PdfDocument(writer)

            pdfDocument.defaultPageSize = PageSize.A4.rotate()

            val converterProperties = ConverterProperties()
            converterProperties.fontProvider = fontProvider

            HtmlConverter.convertToPdf(htmlContent, pdfDocument, converterProperties)

            logger.info("PDF успешно сгенерирован (iText) для сертификата ID: {}", certificate.uniqueCertificateId)
            return outputStream.toByteArray()

        } catch (e: Exception) {
            logger.error("Ошибка iText при генерации PDF: {}", e.message, e)
            throw RuntimeException("Ошибка iText при генерации PDF", e)
        } finally {
            outputStream.close()
        }
    }
}