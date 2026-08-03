package com.dadcoach.workflow.message;

/**
 * Exception thrown when AI message generation fails.
 * 
 * <p>This exception indicates that the {@link MessageGenerator} was unable
 * to generate a message using AI. Common causes include:</p>
 * 
 * <ul>
 *   <li>AI service timeout (exceeds 5-second budget per Requirement 10.6)</li>
 *   <li>AI service unavailable or returning errors</li>
 *   <li>Rate limit exceeded (max 2 AI calls per user per day)</li>
 *   <li>Invalid or missing context data</li>
 * </ul>
 * 
 * <p>When this exception is thrown from {@link MessageGenerator#generate},
 * callers should fall back to using template-based messages. The
 * {@link MessageGenerator#generateWithFallback} method handles this
 * automatically and is recommended for production use.</p>
 * 
 * @see MessageGenerator
 */
public class MessageGenerationException extends RuntimeException {
    
    private final MessageType messageType;
    private final String language;
    
    /**
     * Constructs a new MessageGenerationException with the specified message.
     * 
     * @param message the detail message
     */
    public MessageGenerationException(String message) {
        super(message);
        this.messageType = null;
        this.language = null;
    }
    
    /**
     * Constructs a new MessageGenerationException with the specified message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public MessageGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.messageType = null;
        this.language = null;
    }
    
    /**
     * Constructs a new MessageGenerationException with context about the failed generation.
     * 
     * @param message the detail message
     * @param messageType the type of message that failed to generate
     * @param language the target language for the message
     */
    public MessageGenerationException(String message, MessageType messageType, String language) {
        super(message);
        this.messageType = messageType;
        this.language = language;
    }
    
    /**
     * Constructs a new MessageGenerationException with context and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @param messageType the type of message that failed to generate
     * @param language the target language for the message
     */
    public MessageGenerationException(String message, Throwable cause, 
                                       MessageType messageType, String language) {
        super(message, cause);
        this.messageType = messageType;
        this.language = language;
    }
    
    /**
     * Returns the type of message that failed to generate.
     * 
     * @return the message type, or null if not specified
     */
    public MessageType getMessageType() {
        return messageType;
    }
    
    /**
     * Returns the target language for the message that failed to generate.
     * 
     * @return the language code ("en" or "he"), or null if not specified
     */
    public String getLanguage() {
        return language;
    }
}
