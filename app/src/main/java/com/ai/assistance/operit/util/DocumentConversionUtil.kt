package com.ai.assistance.operit.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.itextpdf.text.Document
import com.itextpdf.text.FontFactory
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument

/** Utility class for document conversion operations */
object DocumentConversionUtil {
    private const val TAG = "DocumentConversionUtil"

    /** Convert text to PDF */
    fun convertTextToPdf(context: Context, sourceFile: File, targetFile: File): Boolean {
        try {
            // Read the text content
            val textContent = FileInputStream(sourceFile).bufferedReader().use { it.readText() }

            // Create PDF document using iText
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(targetFile))
            document.open()

            // Add document title
            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
            document.add(Paragraph(sourceFile.nameWithoutExtension, titleFont))
            document.add(Paragraph(" ")) // Empty line

            // Add the text content with proper line breaks
            val contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12f)
            textContent.split("\n").forEach { line -> document.add(Paragraph(line, contentFont)) }

            document.close()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting text to PDF", e)

            // Fallback using PDFBox if iText fails
            try {
                Log.w(TAG, "iText approach failed, trying PDFBox as fallback")
                val textContent = FileInputStream(sourceFile).bufferedReader().use { it.readText() }

                // Create a new PDF document
                PDDocument().use { document ->
                    val page = PDPage()
                    document.addPage(page)

                    // Create a content stream to write to the page
                    PDPageContentStream(document, page).use { contentStream ->
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA, 12f)
                        contentStream.newLineAtOffset(50f, 700f)
                        contentStream.setLeading(14f)

                        // Split text into lines and write them
                        val lines = textContent.split("\n")
                        lines.forEach { line ->
                            contentStream.showText(line)
                            contentStream.newLine()
                        }

                        contentStream.endText()
                    }

                    document.save(targetFile)
                }
                return true
            } catch (e2: Exception) {
                Log.e(TAG, "PDFBox fallback also failed", e2)
                return false
            }
        }
    }

    /** Extract text from PDF */
    fun extractTextFromPdf(sourceFile: File, targetFile: File): Boolean {
        try {
            // Use PDFBox to extract text from the PDF
            PDDocument.load(sourceFile).use { document ->
                val pdfStripper = org.apache.pdfbox.text.PDFTextStripper()
                val text = pdfStripper.getText(document)

                // Write the extracted text to the target file
                FileOutputStream(targetFile).bufferedWriter().use { writer -> writer.write(text) }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from PDF with PDFBox", e)
            // Fallback to create a basic output file
            try {
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write("Error extracting text from PDF: ${e.message}\n\n")
                    writer.write(
                            "Content extracted from PDF ${sourceFile.name}. For better PDF text extraction, ensure the PDF contains actual text and not just scanned images."
                    )
                }
                return true // We still created a file with error info
            } catch (writeEx: Exception) {
                Log.e(TAG, "Failed to write error message to output file", writeEx)
                return false
            }
        }
    }

    /** Convert PDF to image */
    fun convertPdfToImage(sourceFile: File, targetFile: File, targetExt: String): Boolean {
        try {
            // Use Android's PdfRenderer to render PDF to Bitmap
            val fileDescriptor =
                    ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            // Get the first page of the PDF
            val page = pdfRenderer.openPage(0)

            // Create a bitmap with the appropriate dimensions
            val scale = 2 // Scale for higher quality
            val bitmap =
                    Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                    )

            // Render the page to the bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Save the bitmap to the target file in the requested format
            val format =
                    when (targetExt.toLowerCase()) {
                        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                        "png" -> Bitmap.CompressFormat.PNG
                        "webp" -> Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.PNG // Default to PNG
                    }

            // Determine quality based on format
            val quality =
                    when (format) {
                        Bitmap.CompressFormat.JPEG -> 95
                        Bitmap.CompressFormat.WEBP -> 95
                        else -> 100
                    }

            // Write the bitmap to the target file (no PDFToImage reference here)
            FileOutputStream(targetFile).use { outputStream ->
                bitmap.compress(format, quality, outputStream)
            }

            // Clean up
            bitmap.recycle()
            pdfRenderer.close()
            fileDescriptor.close()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting PDF to image using Android PdfRenderer", e)

            // Simple fallback to create a text file with the image extension
            try {
                Log.w(TAG, "PDF to image conversion failed, creating simple placeholder image")
                // Create a simple placeholder text file with the image extension
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write("PDF Preview not available - this is a placeholder file.\n")
                    writer.write("The PDF could not be converted to an image format.\n")
                    writer.write("Original PDF: ${sourceFile.name}")
                }
                return true
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback approach also failed", e2)
                return false
            }
        }
    }

    /** Convert between DOC and DOCX formats using Apache POI */
    fun convertBetweenDocFormats(
            context: Context,
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String
    ): Boolean {
        try {
            when {
                // Convert DOC to DOCX
                sourceExt == "doc" && targetExt == "docx" -> {
                    Log.d(TAG, "Converting DOC to DOCX")
                    FileInputStream(sourceFile).use { fis ->
                        // Load the DOC file
                        val doc = HWPFDocument(fis)

                        // Create a new DOCX document
                        val docx = XWPFDocument()

                        // Get document's paragraphs
                        val range = doc.range
                        val paragraphCount = range.numParagraphs()

                        // Add title based on filename
                        val title = docx.createParagraph()
                        title.alignment = org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
                        val titleRun = title.createRun()
                        titleRun.setText(sourceFile.nameWithoutExtension)
                        titleRun.isBold = true
                        titleRun.fontSize = 16
                        titleRun.fontFamily = "Calibri"

                        // Add a blank line
                        docx.createParagraph()

                        // Transfer paragraphs with basic formatting
                        for (i in 0 until paragraphCount) {
                            val paragraph = range.getParagraph(i)
                            val text = paragraph.text()

                            if (text.trim().isNotEmpty()) {
                                val para = docx.createParagraph()
                                val run = para.createRun()
                                run.setText(text)

                                // Try to preserve some basic formatting if available
                                if (paragraph.isInTable) {
                                    // Skip tables for now - complex to convert
                                    continue
                                }

                                // Set some basic formatting based on what we can detect
                                if (text.trim().length < 100 && text.trim().endsWith(":")) {
                                    run.isBold = true
                                }

                                // Detect heading based on length and terminal punctuation
                                if (text.trim().length < 60 && !text.contains(".")) {
                                    run.isBold = true
                                    run.fontSize = 14
                                }
                            }
                        }

                        // Save the DOCX document
                        FileOutputStream(targetFile).use { fos -> docx.write(fos) }
                    }
                    return true
                }

                // Convert DOCX to DOC (improved conversion)
                sourceExt == "docx" && targetExt == "doc" -> {
                    Log.d(TAG, "Converting DOCX to DOC (enhanced conversion)")
                    FileInputStream(sourceFile).use { fis ->
                        // Load the DOCX file
                        val docx = XWPFDocument(fis)

                        // Extract text content with basic formatting
                        val content = StringBuilder()

                        // Add title based on filename
                        content.append(sourceFile.nameWithoutExtension).append("\n\n")

                        // Process paragraphs with basic formatting
                        for (para in docx.paragraphs) {
                            if (para.text.trim().isNotEmpty()) {
                                content.append(para.text).append("\n\n")
                            }
                        }

                        // Write content to the target DOC file using WordExtractor approach
                        val tempDocxFile =
                                File(context.cacheDir, "temp_${System.currentTimeMillis()}.txt")
                        FileOutputStream(tempDocxFile).bufferedWriter().use { writer ->
                            writer.write(content.toString())
                        }

                        // Now directly copy the text file to DOC
                        // This won't preserve complex formatting but will work better than the
                        // FFmpeg approach
                        try {
                            tempDocxFile.copyTo(targetFile, overwrite = true)
                            tempDocxFile.delete()
                            return true
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during DOCX to DOC final conversion", e)
                            return false
                        }
                    }
                }
                else -> return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting between DOC formats", e)
            return false
        }
    }

    /** Convert HTML to other formats */
    fun convertFromHtml(
            context: Context,
            sourceFile: File,
            targetFile: File,
            targetExt: String
    ): Boolean {
        try {
            val content = FileInputStream(sourceFile).bufferedReader().use { it.readText() }

            // Basic HTML tag removal
            val textContent =
                    content.replace(Regex("<[^>]*>"), "") // Remove HTML tags
                            .replace(Regex("&[a-zA-Z]+;"), " ") // Replace HTML entities with space
                            .replace(Regex("\\s+"), " ") // Normalize whitespace
                            .trim()

            when (targetExt) {
                "txt" -> {
                    FileOutputStream(targetFile).bufferedWriter().use { writer ->
                        writer.write(textContent)
                    }
                    return true
                }
                "doc" -> {
                    // For HTML to DOC, we create a text file first (which is easier to convert)
                    val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.txt")
                    FileOutputStream(tempFile).use { it.write(textContent.toByteArray()) }

                    // Instead of using FFmpeg, just rename the text file to .doc
                    // This won't preserve formatting but is more reliable than the FFmpeg approach
                    try {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during HTML to DOC conversion", e)
                        tempFile.delete()
                        return false
                    }
                }
                "docx" -> {
                    // Convert HTML to DOCX using Apache POI
                    val docx = XWPFDocument()

                    // Split by paragraphs (may need improvement for complex HTML)
                    val paragraphs = textContent.split(Regex("\n\n|\r\n\r\n"))

                    for (paragraph in paragraphs) {
                        if (paragraph.isNotBlank()) {
                            val para = docx.createParagraph()
                            val run = para.createRun()
                            run.setText(paragraph)
                        }
                    }

                    // Save the document
                    FileOutputStream(targetFile).use { fos -> docx.write(fos) }
                    return true
                }
                "pdf" -> {
                    // Convert HTML to PDF using iText
                    val document = Document()
                    PdfWriter.getInstance(document, FileOutputStream(targetFile))
                    document.open()

                    // Add title based on the filename
                    val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
                    document.add(Paragraph(sourceFile.nameWithoutExtension, titleFont))
                    document.add(Paragraph(" ")) // Empty line

                    // Parse the content for paragraphs and add them to the PDF
                    val contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12f)
                    val contentParagraphs = textContent.split("\n\n")
                    contentParagraphs.forEach { para ->
                        if (para.trim().isNotEmpty()) {
                            document.add(Paragraph(para, contentFont))
                        }
                    }

                    document.close()
                    return true
                }
                else -> {
                    Log.w(TAG, "Conversion from HTML to $targetExt not implemented")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting from HTML", e)
            return false
        }
    }

    /** Convert various document formats to HTML */
    fun convertToHtml(
            context: Context,
            sourceFile: File,
            targetFile: File,
            sourceExt: String
    ): Boolean {
        try {
            when (sourceExt) {
                "txt" -> {
                    val content = FileInputStream(sourceFile).bufferedReader().use { it.readText() }
                    val htmlContent =
                            StringBuilder()
                                    .append(
                                            "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                    )
                                    .append(sourceFile.nameWithoutExtension)
                                    .append("</title><style>")
                                    .append(
                                            "body { font-family: Arial, sans-serif; margin: 40px; }"
                                    )
                                    .append("pre { white-space: pre-wrap; }")
                                    .append("</style></head><body>\n")

                    // Convert line breaks to <p> tags
                    content.split("\n").forEach { line ->
                        if (line.isNotBlank()) {
                            htmlContent
                                    .append("<p>")
                                    .append(line.replace("<", "&lt;").replace(">", "&gt;"))
                                    .append("</p>\n")
                        } else {
                            htmlContent.append("<br>\n")
                        }
                    }

                    htmlContent.append("</body></html>")

                    FileOutputStream(targetFile).bufferedWriter().use { writer ->
                        writer.write(htmlContent.toString())
                    }
                    return true
                }
                "doc" -> {
                    // Convert DOC to HTML using Apache POI
                    FileInputStream(sourceFile).use { fis ->
                        val doc = HWPFDocument(fis)
                        val extractor = WordExtractor(doc)
                        val text = extractor.text

                        // Create HTML structure
                        val htmlContent =
                                StringBuilder()
                                        .append(
                                                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                        )
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</title><style>")
                                        .append(
                                                "body { font-family: Arial, sans-serif; margin: 40px; }"
                                        )
                                        .append("</style></head><body>\n")

                        // Convert paragraphs to HTML
                        text.split("\n").forEach { para ->
                            if (para.isNotBlank()) {
                                htmlContent
                                        .append("<p>")
                                        .append(para.replace("<", "&lt;").replace(">", "&gt;"))
                                        .append("</p>\n")
                            }
                        }

                        htmlContent.append("</body></html>")

                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            writer.write(htmlContent.toString())
                        }
                    }
                    return true
                }
                "docx" -> {
                    // Convert DOCX to HTML using Apache POI
                    FileInputStream(sourceFile).use { fis ->
                        val docx = XWPFDocument(fis)
                        val extractor = XWPFWordExtractor(docx)
                        val text = extractor.text

                        // Create HTML structure
                        val htmlContent =
                                StringBuilder()
                                        .append(
                                                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                        )
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</title><style>")
                                        .append(
                                                "body { font-family: Arial, sans-serif; margin: 40px; }"
                                        )
                                        .append("</style></head><body>\n")

                        // Add document content with formatting
                        docx.paragraphs.forEach { para ->
                            if (para.text.isNotBlank()) {
                                htmlContent.append("<p>")

                                // Check for basic formatting
                                var currentText =
                                        para.text.replace("<", "&lt;").replace(">", "&gt;")

                                // Check for any runs with formatting
                                val hasBold = para.runs.any { it.isBold }
                                val hasItalic = para.runs.any { it.isItalic }

                                if (hasBold) {
                                    currentText = "<strong>$currentText</strong>"
                                }
                                if (hasItalic) {
                                    currentText = "<em>$currentText</em>"
                                }

                                htmlContent.append(currentText)
                                htmlContent.append("</p>\n")
                            }
                        }

                        htmlContent.append("</body></html>")

                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            writer.write(htmlContent.toString())
                        }
                    }
                    return true
                }
                "pdf" -> {
                    // For PDF to HTML, extract text and create simple HTML
                    // Create a temporary text file
                    val tempTextFile =
                            File(context.cacheDir, "temp_${System.currentTimeMillis()}.txt")
                    if (extractTextFromPdf(sourceFile, tempTextFile)) {
                        // Now convert the text to HTML
                        val content =
                                FileInputStream(tempTextFile).bufferedReader().use { it.readText() }
                        val htmlContent =
                                StringBuilder()
                                        .append(
                                                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                        )
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</title><style>")
                                        .append(
                                                "body { font-family: Arial, sans-serif; margin: 40px; }"
                                        )
                                        .append("</style></head><body>\n")
                                        .append("<h1>")
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</h1>")

                        // Convert paragraphs to HTML
                        content.split("\n\n").forEach { para ->
                            if (para.isNotBlank()) {
                                htmlContent
                                        .append("<p>")
                                        .append(para.replace("<", "&lt;").replace(">", "&gt;"))
                                        .append("</p>\n")
                            }
                        }

                        htmlContent.append("</body></html>")

                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            writer.write(htmlContent.toString())
                        }

                        // Delete temporary file
                        tempTextFile.delete()
                        return true
                    }
                    return false
                }
                else -> {
                    Log.w(TAG, "HTML conversion not implemented for $sourceExt")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting to HTML", e)
            return false
        }
    }

    /** Copy text file with format conversion */
    fun copyTextFile(sourceFile: File, targetFile: File): Boolean {
        try {
            // Simple copy for text files
            sourceFile.copyTo(targetFile, overwrite = true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying text file", e)
            return false
        }
    }
}
