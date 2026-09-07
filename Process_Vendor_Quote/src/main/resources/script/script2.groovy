import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {
    def body = message.getBody(String)
    def parser = new JsonSlurper()
    def responseJson = parser.parseText(body)

    // Extract text from Gemini's response structure
    if (!responseJson.candidates || responseJson.candidates.isEmpty()) {
        throw new RuntimeException("Gemini returned no candidates: " + body)
    }

    String rawText = responseJson.candidates[0].content.parts[0].text

    // Clean any accidental markdown code blocks if present
    rawText = rawText.replaceAll(/^```json\s*/, "").replaceAll(/```$/, "").trim()

    def parsedData = parser.parseText(rawText)

    def properties = message.getProperties()
    String quoteId = properties.get("quoteId")

    boolean anyNeedsReview = false
    def lineItems = []
    BigDecimal totalAmount = 0.0

    parsedData.items.eachWithIndex { item, index ->
        double conf = item.confidence != null ? item.confidence.toDouble() : 0.70
        boolean itemNeedsReview = conf < 0.80

        if (itemNeedsReview) {
            anyNeedsReview = true
        }

        BigDecimal qty   = new BigDecimal(item.quantity != null ? item.quantity.toString() : "0")
        BigDecimal price = new BigDecimal(item.unitPrice != null ? item.unitPrice.toString() : "0")
        totalAmount += (qty * price)

        lineItems.add([
            ID             : UUID.randomUUID().toString(),
            quote_ID       : quoteId,
            itemNumber     : item.itemNumber ?: (index + 1) * 10,
            materialNumber : item.materialNumber ?: "UNKNOWN",
            description    : item.description ?: "",
            quantity       : qty,
            unitPrice      : price,
            leadTimeDays   : item.leadTimeDays ?: 0,
            confidenceScore: conf,
            needsReview    : itemNeedsReview,
            isReviewed     : !itemNeedsReview
        ])
    }

    String finalStatus = anyNeedsReview ? "NEEDS_REVIEW" : "READY"

    // Set properties for CAP callback
    message.setProperty("finalStatus", finalStatus)
    message.setProperty("totalAmount", totalAmount.toString())
    message.setProperty("lineItemsJson", JsonOutput.toJson(lineItems))

    // Set body to line items for the callback step
    message.setBody(JsonOutput.toJson(lineItems))
    return message
}