package ai.driftkit.audio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for audio processing
 */
@Data
@ConfigurationProperties(prefix = "audio.processing")
public class AudioProcessingConfig {
    
    /**
     * Transcription engine to use.
     * Default: ASSEMBLYAI
     */
    private EngineType engine = EngineType.ASSEMBLYAI;
    
    /**
     * Processing mode for transcription.
     * Default: BATCH
     */
    private ProcessingMode processingMode = ProcessingMode.BATCH;
    
    // Engine Configuration
    private AssemblyAIConfig assemblyai = new AssemblyAIConfig();
    private DeepgramConfig deepgram = new DeepgramConfig();
    
    // Audio Format Settings
    private int sampleRate = 16000;
    private int bufferSize = 4096;
    private int bufferSizeMs = 100;
    
    // Chunk Duration Settings
    private int maxChunkDurationSeconds = 60;
    private int minChunkDurationSeconds = 2;
    
    // Voice Activity Detection
    private VadConfig vad = new VadConfig();
    
    // Debug Settings
    private DebugConfig debug = new DebugConfig();
    
    // Performance and Resource Settings
    private int maxChunkSizeKb = 1024;
    private int maxBufferSizeMb = 10;
    private int processingTimeoutMs = 30000;
}