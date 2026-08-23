package com.dadcoach.workflow.pattern;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pattern definitions for each workflow state.
 * 
 * <p>This class defines all regex patterns used to match user messages in each
 * workflow state. Patterns are evaluated in order by the PatternMatcher;
 * the first match wins.</p>
 * 
 * <p><b>IMPORTANT:</b> Dad Coach supports ONLY English (en) and Hebrew (he) - NO Spanish anywhere.</p>
 * 
 * <p>Language handling:</p>
 * <ul>
 *   <li><b>English (en)</b>: Primary patterns with (?i) flag for case-insensitive matching</li>
 *   <li><b>Hebrew (he)</b>: Separate patterns for Hebrew text (no case flag needed for Hebrew)</li>
 * </ul>
 * 
 * <p>Implements Requirements 4.2, 5.2, 7.2, 7.3, 9.4, 11.3 - Pattern-based message 
 * processing without AI interpretation.</p>
 * 
 * @see StatePattern
 * @see PatternMatcher
 * @see WorkflowAction
 */
public final class StatePatterns {

    private StatePatterns() {
        // Utility class - prevent instantiation
    }

    // ========================================================================
    // GLOBAL Frustration Patterns (Requirements 2.13, 2.14)
    // ========================================================================
    
    /**
     * Global frustration patterns that can match in any state.
     * Should be checked FIRST before state-specific patterns.
     * 
     * <p>Frustration indicators include:</p>
     * <ul>
     *   <li><b>FRUSTRATION_EN</b> (English): why again|repeat|already said|already told|you asked|asked before → ACKNOWLEDGE_FRUSTRATION</li>
     *   <li><b>FRUSTRATION_HE</b> (Hebrew): למה שוב|כבר אמרתי|שאלת כבר|אתה שואל שוב|חוזר על → ACKNOWLEDGE_FRUSTRATION</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 2.13, 2.14</b> - Detect frustration pattern and respond with empathy.</p>
     */
    public static final List<StatePattern> FRUSTRATION_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // FRUSTRATION_EN (English): why again|repeat|already said|already told|you asked|asked before → ACKNOWLEDGE_FRUSTRATION
        // Matches English frustration expressions indicating the user feels
        // they are being asked to repeat themselves or the bot is repeating
        // Case-insensitive matching with (?i) flag
        // ----------------------------------------------------------------
        StatePattern.of(
            "FRUSTRATION_EN",
            Pattern.compile("(?i).*(why again|repeat|already said|already told|you asked|asked before).*"),
            WorkflowAction.ACKNOWLEDGE_FRUSTRATION
        ),
        
        // ----------------------------------------------------------------
        // FRUSTRATION_HE (Hebrew): למה שוב|כבר אמרתי|שאלת כבר|אתה שואל שוב|חוזר על → ACKNOWLEDGE_FRUSTRATION
        // למה שוב = lama shuv (why again)
        // כבר אמרתי = kvar amarti (I already said)
        // שאלת כבר = sha'alta kvar (you already asked)
        // אתה שואל שוב = ata sho'el shuv (you're asking again)
        // חוזר על = chozer al (repeating)
        // ----------------------------------------------------------------
        StatePattern.of(
            "FRUSTRATION_HE",
            Pattern.compile(".*(למה שוב|כבר אמרתי|שאלת כבר|אתה שואל שוב|חוזר על).*"),
            WorkflowAction.ACKNOWLEDGE_FRUSTRATION
        )
    );

    // ========================================================================
    // WELCOME State Patterns (Requirement 4.2)
    // ========================================================================
    
    /**
     * Patterns for the WELCOME workflow state.
     * 
     * <p>Expected user responses when in WELCOME state (new fathers):</p>
     * <ul>
     *   <li><b>AFFIRMATIVE_EN</b> (English): yes|ready|let's go|ok|sure|start → TRANSITION_TO_SCHEDULE</li>
     *   <li><b>AFFIRMATIVE_HE</b> (Hebrew): כן|מוכן|יאללה|בסדר|התחל → TRANSITION_TO_SCHEDULE</li>
     *   <li><b>MORE_INFO_EN</b> (English): how|what is|explain|tell me more → EXPLAIN_AND_REPROMPT</li>
     *   <li><b>MORE_INFO_HE</b> (Hebrew): איך|מה זה|הסבר|ספר לי עוד → EXPLAIN_AND_REPROMPT</li>
     * </ul>
     * 
     * <p><b>Validates: Requirement 4.2</b> - The WELCOME state accepts exactly two response patterns
     * (affirmative and request for more information) in both English and Hebrew.</p>
     */
    public static final List<StatePattern> WELCOME_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // AFFIRMATIVE_EN (English): yes|ready|let's go|ok|sure|start → TRANSITION_TO_SCHEDULE
        // Matches affirmative responses indicating readiness to schedule
        // Case-insensitive matching with (?i) flag
        // ----------------------------------------------------------------
        StatePattern.of(
            "AFFIRMATIVE_EN",
            Pattern.compile("(?i)^(yes|ready|let's go|let's go|lets go|ok|okay|sure|start).*"),
            WorkflowAction.TRANSITION_TO_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // AFFIRMATIVE_HE (Hebrew): כן|מוכן|יאללה|בסדר|התחל → TRANSITION_TO_SCHEDULE
        // כן = ken (yes)
        // מוכן = muchan (ready)
        // יאללה = yalla (let's go)
        // בסדר = beseder (ok/alright)
        // התחל = hatchel (start)
        // Note: Allows optional emoji/whitespace prefix (e.g., "🚀 התחל")
        // ----------------------------------------------------------------
        StatePattern.of(
            "AFFIRMATIVE_HE",
            Pattern.compile("^[\\p{So}\\p{Cn}\\s]*(כן|מוכן|יאללה|בסדר|התחל).*"),
            WorkflowAction.TRANSITION_TO_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // MORE_INFO_EN (English): how|what is|explain|tell me more → EXPLAIN_AND_REPROMPT
        // Matches requests for additional information about Dad Coach
        // Case-insensitive matching with (?i) flag
        // ----------------------------------------------------------------
        StatePattern.of(
            "MORE_INFO_EN",
            Pattern.compile("(?i).*(how|what is|explain|tell me more).*"),
            WorkflowAction.EXPLAIN_AND_REPROMPT
        ),
        
        // ----------------------------------------------------------------
        // MORE_INFO_HE (Hebrew): איך|מה זה|הסבר|ספר לי עוד → EXPLAIN_AND_REPROMPT
        // איך = eich (how)
        // מה זה = ma ze (what is)
        // הסבר = hesber (explain)
        // ספר לי עוד = saper li od (tell me more)
        // ----------------------------------------------------------------
        StatePattern.of(
            "MORE_INFO_HE",
            Pattern.compile(".*(איך|מה זה|הסבר|ספר לי עוד).*"),
            WorkflowAction.EXPLAIN_AND_REPROMPT
        )
    );

    // ========================================================================
    // SCHEDULE_QUALITY_TIME State Patterns (Requirement 5.2)
    // ========================================================================
    
    /**
     * Patterns for the SCHEDULE_QUALITY_TIME workflow state.
     * 
     * <p>Expected user responses when scheduling Quality Time:</p>
     * <ul>
     *   <li><b>SLOT_NUMBER</b>: Single digit 1-9 to select a slot → SELECT_SLOT</li>
     *   <li><b>SKIP</b> (English): skip|not now|later → POSTPONE_SCHEDULING</li>
     *   <li><b>SKIP_HE</b> (Hebrew): דלג|לא עכשיו|אחר כך → POSTPONE_SCHEDULING</li>
     *   <li><b>MORE_SLOTS</b> (English): other|more|different → SHOW_MORE_SLOTS</li>
     *   <li><b>MORE_SLOTS_HE</b> (Hebrew): אחר|עוד|אחרים → SHOW_MORE_SLOTS</li>
     *   <li><b>TIME_EXPRESSION</b> (English): tomorrow|day patterns|time patterns → PARSE_TIME</li>
     *   <li><b>TIME_EXPRESSION_HE</b> (Hebrew): מחר|day patterns|time expressions → PARSE_TIME</li>
     * </ul>
     * 
     * <p><b>Validates: Requirement 5.2</b> - Slot selection by number, skip, more slots, 
     * and time expressions.</p>
     */
    public static final List<StatePattern> SCHEDULE_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // GREETING (Hebrew): היי|שלום|הי → RESET_TO_WELCOME
        // When user sends a greeting while in SCHEDULE state, reset to welcome
        // This handles cases where user wants to start fresh
        // ----------------------------------------------------------------
        StatePattern.of(
            "GREETING_HE",
            Pattern.compile("^[\\p{So}\\p{Cn}\\s]*(היי|שלום|הי|בוקר טוב|ערב טוב).*"),
            WorkflowAction.RESET_TO_WELCOME
        ),
        
        // ----------------------------------------------------------------
        // GREETING (English): hi|hello|hey → RESET_TO_WELCOME
        // ----------------------------------------------------------------
        StatePattern.of(
            "GREETING_EN",
            Pattern.compile("(?i)^(hi|hello|hey|good morning|good evening).*"),
            WorkflowAction.RESET_TO_WELCOME
        ),
        
        // ----------------------------------------------------------------
        // SLOT_NUMBER: ^([1-9])$ → SELECT_SLOT
        // Matches single digits 1-9 for slot selection
        // ----------------------------------------------------------------
        StatePattern.of(
            "SLOT_NUMBER",
            Pattern.compile("^([1-9])$"),
            WorkflowAction.SELECT_SLOT
        ),
        
        // ----------------------------------------------------------------
        // SKIP (English): skip|not now|later → POSTPONE_SCHEDULING
        // ----------------------------------------------------------------
        StatePattern.of(
            "SKIP",
            Pattern.compile("(?i)^(skip|not now|later).*"),
            WorkflowAction.POSTPONE_SCHEDULING
        ),
        
        // ----------------------------------------------------------------
        // SKIP (Hebrew): דלג|לא עכשיו|אחר כך → POSTPONE_SCHEDULING
        // דלג = daleg (skip)
        // לא עכשיו = lo achshav (not now)
        // אחר כך = achar kach (later)
        // ----------------------------------------------------------------
        StatePattern.of(
            "SKIP_HE",
            Pattern.compile("^(דלג|לא עכשיו|אחר כך).*"),
            WorkflowAction.POSTPONE_SCHEDULING
        ),
        
        // ----------------------------------------------------------------
        // TIME_EXPRESSION (English): tomorrow|day patterns|time patterns → PARSE_TIME
        // NOTE: Must come BEFORE MORE_SLOTS to avoid false matches
        // Matches:
        //   - "tomorrow"
        //   - Day names: monday, tuesday, wednesday, thursday, friday, saturday, sunday
        //   - Time in HH:MM format: e.g., "15:00", "3:30"
        //   - Time with am/pm: e.g., "3pm", "3 pm", "10am"
        //   - Time of day expressions: "in the morning/afternoon/evening"
        // ----------------------------------------------------------------
        StatePattern.of(
            "TIME_EXPRESSION",
            Pattern.compile("(?i).*(tomorrow|" +
                           "monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                           "\\d{1,2}:\\d{2}|\\d{1,2}\\s*(am|pm)|" +
                           "in the (morning|afternoon|evening)).*"),
            WorkflowAction.PARSE_TIME
        ),
        
        // ----------------------------------------------------------------
        // TIME_EXPRESSION (Hebrew): מחר|day patterns|time expressions → PARSE_TIME
        // NOTE: Must come BEFORE MORE_SLOTS_HE because "אחר הצהריים" (afternoon)
        //       contains "אחר" which would otherwise match MORE_SLOTS_HE
        // מחר = machar (tomorrow)
        // Day names:
        //   יום ראשון = yom rishon (Sunday)
        //   יום שני = yom sheni (Monday)
        //   יום שלישי = yom shlishi (Tuesday)
        //   יום רביעי = yom revi'i (Wednesday)
        //   יום חמישי = yom chamishi (Thursday)
        //   יום שישי = yom shishi (Friday)
        //   שבת = shabbat (Saturday)
        // Time of day:
        //   בבוקר = baboker (in the morning)
        //   אחר הצהריים = achar hatsohorayim (in the afternoon)
        //   בערב = ba'erev (in the evening)
        // ----------------------------------------------------------------
        StatePattern.of(
            "TIME_EXPRESSION_HE",
            Pattern.compile(".*(מחר|" +
                           "יום ראשון|יום שני|יום שלישי|יום רביעי|יום חמישי|יום שישי|שבת|" +
                           "בבוקר|אחר הצהריים|בערב).*"),
            WorkflowAction.PARSE_TIME
        ),
        
        // ----------------------------------------------------------------
        // MORE_SLOTS (English): other|more|different → SHOW_MORE_SLOTS
        // ----------------------------------------------------------------
        StatePattern.of(
            "MORE_SLOTS",
            Pattern.compile("(?i).*(other|more|different).*"),
            WorkflowAction.SHOW_MORE_SLOTS
        ),
        
        // ----------------------------------------------------------------
        // MORE_SLOTS (Hebrew): אחר|עוד|אחרים → SHOW_MORE_SLOTS
        // אחר = acher (other)
        // עוד = od (more)
        // אחרים = acherim (others/different)
        // ----------------------------------------------------------------
        StatePattern.of(
            "MORE_SLOTS_HE",
            Pattern.compile(".*(אחר|עוד|אחרים).*"),
            WorkflowAction.SHOW_MORE_SLOTS
        ),
        
        // ----------------------------------------------------------------
        // ALREADY_SCHEDULED (Hebrew): כבר קבענו|כבר אמרתי|כבר שאלת → ALREADY_SCHEDULED
        // Handles cases where father says they already scheduled
        // May need to check if there's actually a scheduled Mission
        // ----------------------------------------------------------------
        StatePattern.of(
            "ALREADY_SCHEDULED_HE_SCHEDULE",
            Pattern.compile(".*(כבר קבענו|כבר אמרתי|כבר שאלת|קבענו כבר).*"),
            WorkflowAction.ALREADY_SCHEDULED
        ),
        
        // ----------------------------------------------------------------
        // ALREADY_SCHEDULED (English): already scheduled|we already|you asked → ALREADY_SCHEDULED
        // ----------------------------------------------------------------
        StatePattern.of(
            "ALREADY_SCHEDULED_SCHEDULE",
            Pattern.compile("(?i).*(already scheduled|we already|you asked|already set).*"),
            WorkflowAction.ALREADY_SCHEDULED
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (Hebrew): אוקי|טוב|תודה|מעולה|סבבה|יופי|בסדר → ACKNOWLEDGE_SCHEDULE
        // When user acknowledges seeing the slots but hasn't selected yet,
        // re-present the slots (they may need to see them again)
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_HE_SCHEDULE",
            Pattern.compile("^(אוקי|טוב|תודה|מעולה|סבבה|יופי|בסדר)$"),
            WorkflowAction.ACKNOWLEDGE_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (English): ok|okay|thanks|great|sure|got it → ACKNOWLEDGE_SCHEDULE
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_EN_SCHEDULE",
            Pattern.compile("(?i)^(ok|okay|thanks|thank you|great|sure|got it|alright)$"),
            WorkflowAction.ACKNOWLEDGE_SCHEDULE
        )
    );

    // ========================================================================
    // WAITING State Patterns (Requirements 6.4, 6.5)
    // ========================================================================
    
    /**
     * Patterns for the WAITING workflow state.
     * 
     * <p>Expected user responses while waiting for scheduled Quality Time:</p>
     * <ul>
     *   <li><b>REQUEST_IDEAS</b> (English): ideas|activity|suggestions|what can I do → TRANSITION_TO_ACTIVITY_IDEAS</li>
     *   <li><b>REQUEST_IDEAS_HE</b> (Hebrew): רעיונות|פעילות|הצעות|מה אפשר לעשות → TRANSITION_TO_ACTIVITY_IDEAS</li>
     *   <li><b>RESCHEDULE</b> (English): reschedule|change|cancel → RESCHEDULE</li>
     *   <li><b>RESCHEDULE_HE</b> (Hebrew): שנה זמן|שינוי|ביטול → RESCHEDULE</li>
     *   <li><b>SCHEDULE_INQUIRY</b> (English): when|schedule|next → SHOW_SCHEDULE</li>
     *   <li><b>SCHEDULE_INQUIRY_HE</b> (Hebrew): מתי|לוח זמנים|הבא → SHOW_SCHEDULE</li>
     *   <li><b>DASHBOARD</b> (English): dashboard|progress|belt|streak → SHOW_DASHBOARD_SUMMARY</li>
     *   <li><b>DASHBOARD_HE</b> (Hebrew): דשבורד|התקדמות|חגורה|רצף → SHOW_DASHBOARD_SUMMARY</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 6.4, 6.5</b> - WAITING state pattern matching.</p>
     */
    public static final List<StatePattern> WAITING_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // REQUEST_IDEAS (English): ideas|activity|suggestions|what can I do → TRANSITION_TO_ACTIVITY_IDEAS
        // Triggered when father explicitly requests activity suggestions
        // ----------------------------------------------------------------
        StatePattern.of(
            "REQUEST_IDEAS",
            Pattern.compile("(?i).*(ideas|activity|suggestions|what can i do).*"),
            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS
        ),
        
        // ----------------------------------------------------------------
        // REQUEST_IDEAS (Hebrew): רעיונות|פעילות|הצעות|מה אפשר לעשות → TRANSITION_TO_ACTIVITY_IDEAS
        // רעיונות = re'ayonot (ideas)
        // פעילות = pe'ilut (activity)
        // הצעות = hatsa'ot (suggestions)
        // מה אפשר לעשות = ma efshar la'asot (what can I do)
        // ----------------------------------------------------------------
        StatePattern.of(
            "REQUEST_IDEAS_HE",
            Pattern.compile(".*(רעיונות|פעילות|הצעות|מה אפשר לעשות).*"),
            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS
        ),
        
        // ----------------------------------------------------------------
        // RESCHEDULE (English): reschedule|change|cancel → RESCHEDULE
        // Cancels existing QualityTime and transitions to SCHEDULE_QUALITY_TIME
        // ----------------------------------------------------------------
        StatePattern.of(
            "RESCHEDULE",
            Pattern.compile("(?i).*(reschedule|change|cancel).*"),
            WorkflowAction.RESCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // RESCHEDULE (Hebrew): שנה זמן|שינוי|ביטול → RESCHEDULE
        // שנה זמן = shne zman (change time / reschedule)
        // שינוי = shinui (change)
        // ביטול = bitul (cancel)
        // ----------------------------------------------------------------
        StatePattern.of(
            "RESCHEDULE_HE",
            Pattern.compile(".*(שנה זמן|שינוי|ביטול).*"),
            WorkflowAction.RESCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // SCHEDULE_INQUIRY (English): when|schedule|next → SHOW_SCHEDULE
        // Shows next scheduled Quality Time details
        // ----------------------------------------------------------------
        StatePattern.of(
            "SCHEDULE_INQUIRY",
            Pattern.compile("(?i).*(when|schedule|next).*"),
            WorkflowAction.SHOW_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // SCHEDULE_INQUIRY (Hebrew): מתי|לוח זמנים|הבא → SHOW_SCHEDULE
        // מתי = matai (when)
        // לוח זמנים = luach zmanim (schedule)
        // הבא = haba (next)
        // ----------------------------------------------------------------
        StatePattern.of(
            "SCHEDULE_INQUIRY_HE",
            Pattern.compile(".*(מתי|לוח זמנים|הבא).*"),
            WorkflowAction.SHOW_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // DASHBOARD (English): dashboard|progress|belt|streak → SHOW_DASHBOARD_SUMMARY
        // Sends text summary with deep link to web dashboard
        // ----------------------------------------------------------------
        StatePattern.of(
            "DASHBOARD",
            Pattern.compile("(?i).*(dashboard|progress|belt|streak).*"),
            WorkflowAction.SHOW_DASHBOARD_SUMMARY
        ),
        
        // ----------------------------------------------------------------
        // DASHBOARD (Hebrew): דשבורד|התקדמות|חגורה|רצף → SHOW_DASHBOARD_SUMMARY
        // דשבורד = dashboard (loan word)
        // התקדמות = hitkadmut (progress)
        // חגורה = chagura (belt)
        // רצף = retzef (streak)
        // ----------------------------------------------------------------
        StatePattern.of(
            "DASHBOARD_HE",
            Pattern.compile(".*(דשבורד|התקדמות|חגורה|רצף).*"),
            WorkflowAction.SHOW_DASHBOARD_SUMMARY
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (English): ok|okay|thanks|got it|great|perfect → ACKNOWLEDGE_SCHEDULE
        // Simple acknowledgments after scheduling confirmation
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE",
            Pattern.compile("(?i)^(ok|okay|alright|thanks|thank you|got it|great|perfect|cool|sounds good|good)$"),
            WorkflowAction.ACKNOWLEDGE_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (Hebrew): אוקי|טוב|תודה|מעולה|סבבה|יופי → ACKNOWLEDGE_SCHEDULE
        // אוקי = oki (okay)
        // טוב = tov (good)
        // תודה = toda (thanks)
        // מעולה = me'ule (great)
        // סבבה = sababa (cool/okay)
        // יופי = yofi (great)
        // בסדר = beseder (okay/alright)
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_HE",
            Pattern.compile("^(אוקי|טוב|תודה|מעולה|סבבה|יופי|בסדר|אחלה|סבבה|מצוין)$"),
            WorkflowAction.ACKNOWLEDGE_SCHEDULE
        ),
        
        // ----------------------------------------------------------------
        // ALREADY_SCHEDULED (Hebrew): כבר קבענו|כבר אמרתי|כבר שאלת → ALREADY_SCHEDULED
        // Handles cases where father reminds bot they already scheduled
        // ----------------------------------------------------------------
        StatePattern.of(
            "ALREADY_SCHEDULED_HE",
            Pattern.compile(".*(כבר קבענו|כבר אמרתי|כבר שאלת|קבענו כבר).*"),
            WorkflowAction.ALREADY_SCHEDULED
        ),
        
        // ----------------------------------------------------------------
        // ALREADY_SCHEDULED (English): already scheduled|we already|you asked → ALREADY_SCHEDULED
        // ----------------------------------------------------------------
        StatePattern.of(
            "ALREADY_SCHEDULED",
            Pattern.compile("(?i).*(already scheduled|we already|you asked|already set).*"),
            WorkflowAction.ALREADY_SCHEDULED
        )
    );

    // ========================================================================
    // ACTIVITY_IDEAS State Patterns (Requirement 9.4)
    // ========================================================================
    
    /**
     * Patterns for the ACTIVITY_IDEAS workflow state.
     * 
     * <p>Expected user responses when viewing activity ideas:</p>
     * <ul>
     *   <li><b>IDEA_NUMBER</b>: Single digit 1-3 to select an idea → SHOW_IDEA_DETAILS</li>
     *   <li><b>MORE_IDEAS</b> (English): more|another|different → GENERATE_MORE_IDEAS</li>
     *   <li><b>MORE_IDEAS_HE</b> (Hebrew): עוד|אחר|שונה → GENERATE_MORE_IDEAS</li>
     *   <li><b>EXIT</b> (English): thanks|done|enough → RETURN_TO_PREVIOUS</li>
     *   <li><b>EXIT_HE</b> (Hebrew): תודה|סיימתי|מספיק → RETURN_TO_PREVIOUS</li>
     * </ul>
     * 
     * <p><b>Validates: Requirement 9.4</b> - Activity ideas interaction patterns.</p>
     */
    public static final List<StatePattern> ACTIVITY_IDEAS_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // IDEA_NUMBER: ^([1-3])$ → SHOW_IDEA_DETAILS
        // Matches single digits 1-3 for idea selection (3 ideas are presented)
        // ----------------------------------------------------------------
        StatePattern.of(
            "IDEA_NUMBER",
            Pattern.compile("^([1-3])$"),
            WorkflowAction.SHOW_IDEA_DETAILS
        ),
        
        // ----------------------------------------------------------------
        // EXIT (English): thanks|done|enough → RETURN_TO_PREVIOUS
        // NOTE: EXIT patterns come before MORE_IDEAS to ensure "done" doesn't
        // accidentally match if the user says "done with ideas"
        // ----------------------------------------------------------------
        StatePattern.of(
            "EXIT",
            Pattern.compile("(?i)^(thanks|thank you|done|enough|that's enough|got it|perfect).*"),
            WorkflowAction.RETURN_TO_PREVIOUS
        ),
        
        // ----------------------------------------------------------------
        // EXIT (Hebrew): תודה|סיימתי|מספיק → RETURN_TO_PREVIOUS
        // תודה = toda (thanks)
        // סיימתי = siyamti (I'm done)
        // מספיק = maspik (enough)
        // ----------------------------------------------------------------
        StatePattern.of(
            "EXIT_HE",
            Pattern.compile("^(תודה|סיימתי|מספיק|די לי|זהו).*"),
            WorkflowAction.RETURN_TO_PREVIOUS
        ),
        
        // ----------------------------------------------------------------
        // MORE_IDEAS (English): more|another|different → GENERATE_MORE_IDEAS
        // ----------------------------------------------------------------
        StatePattern.of(
            "MORE_IDEAS",
            Pattern.compile("(?i).*(more|another|different|other ideas|new ideas).*"),
            WorkflowAction.GENERATE_MORE_IDEAS
        ),
        
        // ----------------------------------------------------------------
        // MORE_IDEAS (Hebrew): עוד|אחר|שונה → GENERATE_MORE_IDEAS
        // עוד = od (more)
        // אחר = acher (another/different)
        // שונה = shone (different)
        // ----------------------------------------------------------------
        StatePattern.of(
            "MORE_IDEAS_HE",
            Pattern.compile(".*(עוד רעיונות|רעיונות אחרים|אחר|שונה).*"),
            WorkflowAction.GENERATE_MORE_IDEAS
        )
    );

    // ========================================================================
    // QUALITY_TIME_FOLLOW_UP State Patterns (Requirement 7.2, 7.3)
    // ========================================================================
    
    /**
     * Patterns for the QUALITY_TIME_FOLLOW_UP workflow state.
     * 
     * <p>Expected patterns:</p>
     * <ul>
     *   <li><b>COMPLETED</b>: yes|done|completed|finished → MARK_COMPLETED</li>
     *   <li><b>NOT_COMPLETED</b>: no|not yet|couldn't → MARK_MISSED</li>
     * </ul>
     * 
     * <p>Implements Requirements 7.2 and 7.3 - Follow-up response patterns.</p>
     */
    public static final List<StatePattern> FOLLOW_UP_PATTERNS = List.of(
        // COMPLETED (English): yes|done|completed|finished → MARK_COMPLETED
        StatePattern.of(
            "COMPLETED",
            Pattern.compile("(?i)^(yes|done|completed|finished|did it|we did).*"),
            WorkflowAction.MARK_COMPLETED
        ),
        
        // COMPLETED (Hebrew): כן|סיימתי|עשיתי|הושלם → MARK_COMPLETED
        StatePattern.of(
            "COMPLETED_HE",
            Pattern.compile("^(כן|סיימתי|עשיתי|הושלם|עשינו).*"),
            WorkflowAction.MARK_COMPLETED
        ),
        
        // NOT_COMPLETED (English): no|not yet|couldn't → MARK_MISSED
        StatePattern.of(
            "NOT_COMPLETED",
            Pattern.compile("(?i)^(no|not yet|couldn't|didn't|could not|we didn't).*"),
            WorkflowAction.MARK_MISSED
        ),
        
        // NOT_COMPLETED (Hebrew): לא|עוד לא|לא הצלחתי → MARK_MISSED
        StatePattern.of(
            "NOT_COMPLETED_HE",
            Pattern.compile("^(לא|עוד לא|לא הצלחתי|לא עשינו).*"),
            WorkflowAction.MARK_MISSED
        )
    );

    // ========================================================================
    // QUALITY_TIME_REMINDER State Patterns
    // ========================================================================
    
    /**
     * Patterns for the QUALITY_TIME_REMINDER workflow state.
     * 
     * <p>Expected patterns when father receives a pre-QT reminder:</p>
     * <ul>
     *   <li><b>CANCEL</b>: cancel|ביטול → CANCEL_QUALITY_TIME</li>
     *   <li><b>REQUEST_IDEAS</b>: ideas|רעיונות → TRANSITION_TO_ACTIVITY_IDEAS</li>
     *   <li><b>ACKNOWLEDGE</b>: ok|אוקי|thanks → ACKNOWLEDGE_REMINDER</li>
     * </ul>
     */
    public static final List<StatePattern> REMINDER_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // CANCEL (English): cancel|can't make it|won't be able → CANCEL_QUALITY_TIME
        // ----------------------------------------------------------------
        StatePattern.of(
            "CANCEL",
            Pattern.compile("(?i).*(cancel|can't make it|won't be able|can not make|cannot make).*"),
            WorkflowAction.CANCEL_QUALITY_TIME
        ),
        
        // ----------------------------------------------------------------
        // CANCEL (Hebrew): ביטול|לא יכול|לא אוכל → CANCEL_QUALITY_TIME
        // ביטול = bitul (cancel)
        // לא יכול = lo yachol (can't)
        // לא אוכל = lo uchal (won't be able)
        // ----------------------------------------------------------------
        StatePattern.of(
            "CANCEL_HE",
            Pattern.compile(".*(ביטול|לא יכול|לא אוכל|בטל).*"),
            WorkflowAction.CANCEL_QUALITY_TIME
        ),
        
        // ----------------------------------------------------------------
        // REQUEST_IDEAS (English): ideas|activity|suggestions → TRANSITION_TO_ACTIVITY_IDEAS
        // ----------------------------------------------------------------
        StatePattern.of(
            "REQUEST_IDEAS_REMINDER",
            Pattern.compile("(?i).*(ideas|activity|suggestions|what can i do).*"),
            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS
        ),
        
        // ----------------------------------------------------------------
        // REQUEST_IDEAS (Hebrew): רעיונות|פעילות|הצעות → TRANSITION_TO_ACTIVITY_IDEAS
        // ----------------------------------------------------------------
        StatePattern.of(
            "REQUEST_IDEAS_REMINDER_HE",
            Pattern.compile(".*(רעיונות|פעילות|הצעות|מה אפשר לעשות).*"),
            WorkflowAction.TRANSITION_TO_ACTIVITY_IDEAS
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (English): ok|thanks|got it|great → ACKNOWLEDGE_REMINDER
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_REMINDER",
            Pattern.compile("(?i)^(ok|okay|thanks|thank you|got it|great|perfect|awesome|cool).*"),
            WorkflowAction.ACKNOWLEDGE_REMINDER
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (Hebrew): אוקי|טוב|תודה|מעולה → ACKNOWLEDGE_REMINDER
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_REMINDER_HE",
            Pattern.compile("^(אוקי|טוב|תודה|מעולה|סבבה|יופי|בסדר|אחלה|מצוין).*"),
            WorkflowAction.ACKNOWLEDGE_REMINDER
        )
    );

    // ========================================================================
    // INACTIVITY_NUDGE State Patterns
    // ========================================================================
    
    /**
     * Patterns for the INACTIVITY_NUDGE workflow state.
     * 
     * <p>Expected patterns when father responds to inactivity nudge:</p>
     * <ul>
     *   <li><b>SCHEDULE_NOW</b>: schedule|let's do it|קבע → SCHEDULE_NOW</li>
     *   <li><b>PAUSE</b>: pause|break|need time → PAUSE_COACHING</li>
     *   <li>Any other response: re-engagement → ACKNOWLEDGE_REMINDER</li>
     * </ul>
     */
    public static final List<StatePattern> INACTIVITY_PATTERNS = List.of(
        // ----------------------------------------------------------------
        // SCHEDULE_NOW (English): schedule|let's do it|let's go|ready → SCHEDULE_NOW
        // ----------------------------------------------------------------
        StatePattern.of(
            "SCHEDULE_NOW",
            Pattern.compile("(?i).*(schedule|let's do it|let's go|ready|yes|let's schedule).*"),
            WorkflowAction.SCHEDULE_NOW
        ),
        
        // ----------------------------------------------------------------
        // SCHEDULE_NOW (Hebrew): קבע|בוא נקבע|מוכן|יאללה → SCHEDULE_NOW
        // ----------------------------------------------------------------
        StatePattern.of(
            "SCHEDULE_NOW_HE",
            Pattern.compile(".*(קבע|בוא נקבע|מוכן|יאללה|כן).*"),
            WorkflowAction.SCHEDULE_NOW
        ),
        
        // ----------------------------------------------------------------
        // PAUSE (English): pause|break|need time|stop → PAUSE_COACHING
        // ----------------------------------------------------------------
        StatePattern.of(
            "PAUSE",
            Pattern.compile("(?i).*(pause|break|need time|stop|not now|later|busy).*"),
            WorkflowAction.PAUSE_COACHING
        ),
        
        // ----------------------------------------------------------------
        // PAUSE (Hebrew): הפסקה|צריך זמן|עצור|לא עכשיו → PAUSE_COACHING
        // ----------------------------------------------------------------
        StatePattern.of(
            "PAUSE_HE",
            Pattern.compile(".*(הפסקה|צריך זמן|עצור|לא עכשיו|אחר כך|עסוק).*"),
            WorkflowAction.PAUSE_COACHING
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (English): ok|hi|hello → ACKNOWLEDGE_REMINDER (re-engagement)
        // Any positive response is treated as re-engagement
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_INACTIVITY",
            Pattern.compile("(?i)^(ok|okay|hi|hello|hey|thanks|good|fine|alright).*"),
            WorkflowAction.ACKNOWLEDGE_REMINDER
        ),
        
        // ----------------------------------------------------------------
        // ACKNOWLEDGE (Hebrew): אוקי|היי|שלום → ACKNOWLEDGE_REMINDER (re-engagement)
        // ----------------------------------------------------------------
        StatePattern.of(
            "ACKNOWLEDGE_INACTIVITY_HE",
            Pattern.compile("^(אוקי|היי|שלום|טוב|בסדר|מה קורה).*"),
            WorkflowAction.ACKNOWLEDGE_REMINDER
        )
    );
}
