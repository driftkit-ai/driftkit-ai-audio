package ai.driftkit.audio.config;

import lombok.Data;

/**
 * Configuration for AssemblyAI transcription service.
 */
@Data
public class AssemblyAIConfig {
    /**
     * AssemblyAI API key.
     */
    private String apiKey;
    
    /**
     * Language code for transcription.
     * Default: ENGLISH
     */
    private LanguageCode languageCode = LanguageCode.ENGLISH;
}