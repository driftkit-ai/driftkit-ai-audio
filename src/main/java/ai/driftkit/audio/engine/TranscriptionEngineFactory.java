package ai.driftkit.audio.engine;

import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.config.AudioProcessingConfig;
import ai.driftkit.audio.config.EngineType;
import ai.driftkit.audio.config.ProcessingMode;
import ai.driftkit.audio.engine.impl.AssemblyAIEngine;
import ai.driftkit.audio.engine.impl.DeepgramEngine;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating transcription engine instances based on configuration.
 */
@Slf4j
@Component
public class TranscriptionEngineFactory {
    
    private final AudioProcessingConfig config;
    
    public TranscriptionEngineFactory(AudioProcessingConfig config) {
        this.config = config;
    }
    
    /**
     * Create a transcription engine based on the configured engine type.
     * 
     * @return Configured transcription engine
     * @throws IllegalArgumentException if engine type is not supported
     */
    public TranscriptionEngine createEngine() {
        EngineType engineType = config.getEngine();
        
        TranscriptionEngine engine;
        switch (engineType) {
            case ASSEMBLYAI:
                engine = new AssemblyAIEngine(config);
                break;
            case DEEPGRAM:
                engine = new DeepgramEngine(config);
                break;
            default:
                throw new IllegalArgumentException("Unsupported transcription engine: " + engineType);
        }
        
        // Validate processing mode compatibility
        switch (config.getProcessingMode()) {
            case STREAMING:
                if (!engine.supportsStreamingMode()) {
                    throw new IllegalStateException(
                        String.format("Engine '%s' does not support streaming mode", engine.getName()));
                }
                break;
            case BATCH:
                if (!engine.supportsBatchMode()) {
                    throw new IllegalStateException(
                        String.format("Engine '%s' does not support batch mode", engine.getName()));
                }
                break;
        }
        
        engine.initialize();
        
        log.info("Created {} transcription engine in {} mode", 
                engine.getName(), config.getProcessingMode());
        
        return engine;
    }
    
    /**
     * Get the supported engines and their capabilities.
     * 
     * @return Map of engine names to their configurations
     */
    public static Map<EngineType, EngineConfiguration> getSupportedEngines() {
        Map<EngineType, EngineConfiguration> engines = new HashMap<>();
        
        // Add AssemblyAI
        AudioProcessingConfig dummyConfig = new AudioProcessingConfig();
        AssemblyAIEngine assemblyAI = new AssemblyAIEngine(dummyConfig);
        engines.put(EngineType.ASSEMBLYAI, assemblyAI.getConfiguration());
        
        // Add Deepgram
        DeepgramEngine deepgram = new DeepgramEngine(dummyConfig);
        engines.put(EngineType.DEEPGRAM, deepgram.getConfiguration());
        
        return engines;
    }
}