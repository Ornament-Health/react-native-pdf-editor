package com.ornament.pdfeditor.utils

import com.itextpdf.kernel.utils.IXmlParserFactory
import org.xml.sax.helpers.XMLReaderFactory
import java.lang.Exception
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory

class XmlParserFactory : IXmlParserFactory {

    override fun createDocumentBuilderInstance(
        namespaceAware: Boolean,
        ignoringComments: Boolean
    ): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = namespaceAware
        factory.isIgnoringComments = ignoringComments
        try {
            return factory.newDocumentBuilder()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun createXMLReaderInstance(
        namespaceAware: Boolean,
        validating: Boolean
    ) = XMLReaderFactory.createXMLReader().apply {
        setFeature("http://xml.org/sax/features/namespaces", namespaceAware)
        setFeature("http://xml.org/sax/features/validation", validating)
    }

    override fun createTransformerInstance() =
        TransformerFactory.newInstance().newTransformer()

}