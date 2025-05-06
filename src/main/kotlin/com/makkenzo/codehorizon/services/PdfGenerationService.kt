package com.makkenzo.codehorizon.services

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
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
    private val fontProvider: FontProvider

    init {
        fontProvider = FontProvider()
        try {
            val fontPathLiberation = "fonts/LiberationSans-Regular.ttf"
            val fontPathDejaVu = "fonts/DejaVuSans.ttf"

            if (ClassPathResource(fontPathLiberation).exists()) {
                fontProvider.addFont(fontPathLiberation)
                logger.info("Шрифт Liberation Sans добавлен в FontProvider iText.")
            } else {
                logger.warn("Шрифт Liberation Sans не найден в classpath: {}", fontPathLiberation)
            }
            if (ClassPathResource(fontPathDejaVu).exists()) {
                fontProvider.addFont(fontPathDejaVu)
                logger.info("Шрифт DejaVu Sans добавлен в FontProvider iText.")
            } else {
                logger.warn("Шрифт DejaVu Sans не найден в classpath: {}", fontPathDejaVu)
            }
        } catch (e: Exception) {
            logger.error("Ошибка при добавлении шрифтов в FontProvider iText: {}", e.message, e)
        }
        fontProvider.addStandardPdfFonts()
        fontProvider.addSystemFonts()
    }


    fun generateCertificatePdf(certificate: Certificate): ByteArray {
        val context = Context()
        context.setVariable("userName", certificate.userName)
        context.setVariable("courseTitle", certificate.courseTitle)
        context.setVariable("completionDate", dateFormatter.format(certificate.completionDate))
        context.setVariable("certificateId", certificate.uniqueCertificateId)

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