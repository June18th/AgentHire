package com.git.hui.jobclaw.core.agent.soul.collector;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool for AI agent to ask questions to users during soul collection.
 *
 * <p>This tool enables the AI agent to:
 * <ul>
 *   <li>Dynamically generate questions based on context</li>
 *   <li>Wait for user responses asynchronously</li>
 *   <li>Collect answers and build user profile</li>
 * </ul>
 *
 * AIDEV-NOTE: Tool for AI-based soul collection - enables dynamic questioning
 */
@Component
public class AskUserQuestionTool {

    private static final Logger log = LoggerFactory.getLogger(AskUserQuestionTool.class);

    // Store pending questions and their futures
    private final Map<String, PendingQuestion> pendingQuestions = new ConcurrentHashMap<>();

    /**
     * Represents a pending question waiting for user response
     */
    public record PendingQuestion(
            String jobClawUserId,
            String question,
            String category,
            String fieldName,
            CompletableFuture<String> answerFuture
    ) {
    }

    /**
     * Ask a question to the user and wait for response.
     *
     * @param question the question to ask
     * @param category question category (e.g., "education", "job_preferences")
     * @param fieldName the field name this question collects (e.g., "university", "location")
     * @param toolContext tool context containing jobClawUserId
     * @return instruction to the AI about how to proceed
     */
    @Tool(name = "askUserQuestion",
            description = """
                    Use this tool to ask the user a question during profile collection.
                    The tool will send the question to the user and return their answer.
                                        
                    ## When to Use:
                    - When collecting user profile information
                    - When you need to ask follow-up questions
                    - When you want to clarify user preferences
                                        
                    ## Guidelines:
                    - Ask one question at a time
                    - Be friendly and conversational
                    - Explain why you're asking (if not obvious)
                    - Allow users to skip if they prefer
                    - Use Chinese language
                                        
                    ## Categories:
                    - basic_info: Name, graduation year, etc.
                    - education: University, major, degree
                    - job_preferences: Location, job type, internship
                    - skills: Technical skills, languages
                    - experience: Internships, projects, awards
                    """)
    public String askUserQuestion(
            @JsonPropertyDescription("The question to ask the user (in Chinese)")
            String question,
            @JsonPropertyDescription("Question category: basic_info, education, job_preferences, skills, experience")
            String category,
            @JsonPropertyDescription("The field name this question collects (e.g., 'university', 'location')")
            String fieldName,
            ToolContext toolContext) {

        String jobClawUserId = (String) toolContext.getContext().get("jobClawUserId");
        log.info("[AskUserQuestion] AI asking question: field={}, category={}, user={}", 
                fieldName, category, jobClawUserId);

        // Create a future for the answer
        CompletableFuture<String> answerFuture = new CompletableFuture<>();

        // Store pending question
        PendingQuestion pending = new PendingQuestion(
                jobClawUserId,
                question,
                category,
                fieldName,
                answerFuture
        );
        pendingQuestions.put(jobClawUserId + ":" + fieldName, pending);

        // The actual question sending will be handled by AiBasedSoulCollector
        // This tool just registers the question and returns instructions
        return String.format("""
                Question registered: "%s"
                Category: %s
                Field: %s
                
                The question will be sent to the user. 
                Wait for the user's response before continuing.
                Their answer will be provided in the next message.
                """, question, category, fieldName);
    }

    /**
     * Submit an answer to a pending question.
     * Called by AiBasedSoulCollector when user responds.
     *
     * @param jobClawUserId user ID
     * @param fieldName field name
     * @param answer user's answer
     * @return true if answer was submitted successfully
     */
    public boolean submitAnswer(String jobClawUserId, String fieldName, String answer) {
        String key = jobClawUserId + ":" + fieldName;
        PendingQuestion pending = pendingQuestions.remove(key);

        if (pending != null) {
            pending.answerFuture().complete(answer);
            log.info("[AskUserQuestion] Answer submitted: user={}, field={}, answer={}", 
                    jobClawUserId, fieldName, answer);
            return true;
        } else {
            log.warn("[AskUserQuestion] No pending question found: user={}, field={}", 
                    jobClawUserId, fieldName);
            return false;
        }
    }

    /**
     * Get pending question for a user and field.
     *
     * @param jobClawUserId user ID
     * @param fieldName field name
     * @return pending question if exists
     */
    public PendingQuestion getPendingQuestion(String jobClawUserId, String fieldName) {
        return pendingQuestions.get(jobClawUserId + ":" + fieldName);
    }

    /**
     * Cancel all pending questions for a user.
     *
     * @param jobClawUserId user ID
     */
    public void cancelPendingQuestions(String jobClawUserId) {
        pendingQuestions.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(jobClawUserId + ":")) {
                entry.getValue().answerFuture().cancel(true);
                return true;
            }
            return false;
        });
        log.info("[AskUserQuestion] Cancelled pending questions for user: {}", jobClawUserId);
    }

    /**
     * Get count of pending questions.
     *
     * @return pending question count
     */
    public int getPendingQuestionCount() {
        return pendingQuestions.size();
    }
}
