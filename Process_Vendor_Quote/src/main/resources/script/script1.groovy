import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import java.net.URL

def Message processData(Message message) {
    // 1. Parse the incoming JSON body from CAP
    def body = message.getBody(String)
    def parser = new JsonSlurper()
    def data = parser.parseText(body)

    String quoteId     = data.quoteId     ?: ""
    String rfqId       = data.rfqId       ?: ""
    String vendorId    = data.vendorId    ?: ""
    String documentUrl = data.documentUrl ?: ""

    if (!documentUrl) {
        throw new IllegalArgumentException("Field documentUrl is missing from inbound body.")
    }

    // 2. Preserve metadata in Exchange Properties for subsequent steps
    message.setProperty("quoteId", quoteId)
    message.setProperty("rfqId", rfqId)
    message.setProperty("vendorId", vendorId)
    message.setProperty("documentUrl", documentUrl)

    // 3. Download document bytes from the URL
    byte[] fileBytes
    try {
        URL url = new URL(documentUrl)
        URLConnection connection = url.openConnection()
        connection.setConnectTimeout(10000)
        connection.setReadTimeout(15000)
        fileBytes = connection.getInputStream().bytes
    } catch (Exception ex) {
        throw new RuntimeException("Failed downloading document from URL [${documentUrl}]: " + ex.getMessage(), ex)
    }

    // 4. Extract text
    // For plain-text/mock vendor files:
    String documentText = new String(fileBytes, "UTF-8")

    // Clean up excessive whitespace/newlines to optimize LLM token usage
    documentText = documentText.replaceAll(/\r\n|\r/, "\n").replaceAll(/\n{3,}/, "\n\n").trim()

    // Store extracted text into an exchange property
    message.setProperty("extractedDocText", documentText)

    // Set message body to the extracted text for downstream logging/debugging
    message.setBody(documentText)

    return message
}