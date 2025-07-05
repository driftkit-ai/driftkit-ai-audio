# DriftKit Audio Processing Library

A high-performance Java library for real-time audio processing with voice activity detection (VAD) and speech-to-text transcription. The library provides both framework-agnostic core functionality and convenient Spring Boot integration.

## Architecture

The library is built with a modular architecture:

- **Core Module** (`audio-processing-core`): Framework-agnostic audio processing engine
- **Spring Boot Starter** (`audio-processing-spring-boot-starter`): Spring Boot integration and auto-configuration

## Features

- **Real-time Audio Processing**: Process audio streams with low latency
- **Voice Activity Detection (VAD)**: Intelligent speech segment detection
- **Multiple Transcription Engines**:
  - AssemblyAI (batch mode)
  - Deepgram (batch and streaming modes)
- **Processing Modes**:
  - Batch: VAD-based chunking with complete segment transcription
  - Streaming: Real-time transcription with word-level timing
- **Type-safe Configuration**: Enum-based settings for all options
- **Multi-language Support**: 30+ languages with type-safe language codes
- **Session Isolation**: Concurrent processing of multiple audio streams
- **Audio Format Conversion**: Support for WAV, MP3, OGG, FLAC, AAC, M4A
- **Memory Efficient**: Automatic cleanup and segmented results

## Installation

### With Spring Boot (Recommended)

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>audio-processing-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Core Library Only

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>audio-processing-core</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle

```gradle
// With Spring Boot
implementation 'ai.driftkit:audio-processing-spring-boot-starter:1.0.1'

// Core only
implementation 'ai.driftkit:audio-processing-core:1.0.1'
```

## Requirements

- Java 17+
- Maven 3.6+
- FFmpeg (optional, for extended audio format support)

## Quick Start

### Spring Boot Integration

#### 1. Configuration

Add to your `application.yml`:

```yaml
audio:
  processing:
    engine: DEEPGRAM
    processing-mode: STREAMING
    sample-rate: 16000
    buffer-size: 4096
    
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}
      language: ENGLISH
      model: "nova-3"
      punctuate: true
      interim-results: true
```

#### 2. Basic Usage

```java
@Component
public class AudioService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    public void transcribeAudio(String userId, byte[] audioData) {
        // Create session with callback
        sessionManager.createSession(userId, result -> {
            if (!result.isError()) {
                if (result.isInterim()) {
                    System.out.println("[LIVE] " + result.getMergedTranscript());
                } else {
                    System.out.println("[FINAL] " + result.getMergedTranscript());
                }
            }
        });
        
        // Process audio
        sessionManager.processAudioChunk(userId, audioData);
        
        // Close session when done
        sessionManager.closeSession(userId);
    }
}
```

### Core Library Usage (Without Spring)

```java
public class AudioTranscriptionExample {
    
    public void transcribeAudio() {
        // Configure the library
        CoreAudioConfig config = new CoreAudioConfig();
        config.setEngine(EngineType.DEEPGRAM);
        config.setProcessingMode(ProcessingMode.BATCH);
        config.setSampleRate(16000);
        config.setBufferSize(4096);
        
        // Configure Deepgram
        DeepgramConfig deepgramConfig = new DeepgramConfig();
        deepgramConfig.setApiKey("your-api-key");
        deepgramConfig.setLanguage(LanguageCode.ENGLISH);
        deepgramConfig.setModel("nova-3");
        config.setDeepgram(deepgramConfig);
        
        // Create session manager
        AudioSessionManager sessionManager = new AudioSessionManager(config);
        
        // Create session
        sessionManager.createSession("user-123", result -> {
            if (!result.isError()) {
                System.out.println("Transcription: " + result.getText());
            }
        });
        
        // Process audio
        byte[] audioData = captureAudio();
        sessionManager.processAudioChunk("user-123", audioData);
        
        // Clean up
        sessionManager.closeSession("user-123");
        sessionManager.shutdown();
    }
}
```

## Configuration Options

### Engine Types (Type-safe Enums)

```java
EngineType.ASSEMBLYAI  // AssemblyAI transcription service
EngineType.DEEPGRAM    // Deepgram transcription service
```

### Processing Modes

```java
ProcessingMode.BATCH     // VAD-based chunking with complete segments
ProcessingMode.STREAMING // Real-time streaming transcription
```

### Audio Formats

```java
AudioFormatType.WAV      // Lossless WAV format
AudioFormatType.MP3      // Compressed MP3 format
AudioFormatType.OGG      // Ogg Vorbis format
AudioFormatType.FLAC     // Lossless FLAC format
AudioFormatType.AAC      // Advanced Audio Codec
AudioFormatType.M4A      // MPEG-4 Audio
```

### Language Support

```java
LanguageCode.ENGLISH
LanguageCode.SPANISH
LanguageCode.FRENCH
LanguageCode.GERMAN
LanguageCode.CHINESE_SIMPLIFIED
LanguageCode.CHINESE_TRADITIONAL
LanguageCode.JAPANESE
LanguageCode.KOREAN
LanguageCode.RUSSIAN
LanguageCode.ARABIC
LanguageCode.HINDI
// ... and 20+ more languages
```

## Advanced Usage Examples

### Streaming Mode with Word-level Timing

```java
@Component
public class StreamingTranscriptionService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    public void startStreaming(String sessionId) {
        sessionManager.createSession(sessionId, result -> {
            if (result.isError()) {
                log.error("Transcription error: {}", result.getErrorMessage());
                return;
            }
            
            if (result.isInterim()) {
                // Live transcription updates
                displayLiveTranscription(result.getMergedTranscript());
                
                // Show word-level timing for live captions
                result.getWords().forEach(word -> {
                    System.out.printf("%s [%.1f-%.1fs] conf:%.2f\n",
                        word.getPunctuatedWord(),
                        word.getStart(),
                        word.getEnd(),
                        word.getConfidence()
                    );
                });
            } else {
                // Final segment - save permanently
                saveTranscriptionSegment(result.getMergedTranscript());
            }
        });
        
        // Start streaming audio
        streamAudioToSession(sessionId);
    }
}
```

### Multi-language Processing

```java
@Component
public class MultiLanguageService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    public void processMultiLanguageAudio(String sessionId, LanguageCode language) {
        // Create session with language-specific callback
        sessionManager.createSession(sessionId, result -> {
            if (!result.isError()) {
                processLanguageSpecificResult(result, language);
            }
        });
        
        // Process audio with language context
        processAudioWithLanguage(sessionId, language);
    }
    
    private void processLanguageSpecificResult(TranscriptionResult result, LanguageCode language) {
        switch (language) {
            case ENGLISH -> processEnglishText(result.getText());
            case SPANISH -> processSpanishText(result.getText());
            case CHINESE_SIMPLIFIED -> processChineseText(result.getText());
            default -> processGenericText(result.getText());
        }
    }
}
```

### Audio Format Conversion

```java
@Component
public class AudioFormatService {
    
    @Autowired
    private AudioConverter audioConverter;
    
    public byte[] convertAudio(byte[] rawPcmData, AudioFormatType targetFormat) {
        try {
            return audioConverter.convertToFormat(rawPcmData, 16000, targetFormat);
        } catch (IOException | InterruptedException e) {
            log.error("Audio conversion failed", e);
            throw new AudioProcessingException("Failed to convert to " + targetFormat.getDisplayName(), e);
        }
    }
    
    public void demonstrateConversionMethods() {
        // Get conversion capabilities
        AudioConverter.ConversionInfo info = audioConverter.getConversionInfo("mp3");
        System.out.println("MP3 conversion: " + info.getPreferredMethod());
        
        // Check performance characteristics
        AudioConverter.PerformanceInfo perf = audioConverter.getPerformanceInfo("wav");
        System.out.println("WAV performance: " + perf.getSpeed() + " speed, " + perf.getResourceUsage() + " resources");
    }
}
```

### Concurrent Session Management

```java
@Component
public class ConcurrentSessionService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public void processConcurrentSessions(List<String> userIds) {
        userIds.forEach(userId -> {
            executorService.submit(() -> {
                try {
                    sessionManager.createSession(userId, result -> {
                        if (!result.isError()) {
                            storeUserTranscription(userId, result.getText());
                        }
                    });
                    
                    // Process audio for this user
                    processUserAudio(userId);
                    
                } finally {
                    sessionManager.closeSession(userId);
                }
            });
        });
    }
}
```

## Complete Configuration Reference

```yaml
# Spring Boot configuration
spring:
  main:
    allow-circular-references: true  # Required for audio processing

audio:
  processing:
    # Engine and mode selection
    engine: DEEPGRAM                    # ASSEMBLYAI | DEEPGRAM
    processing-mode: STREAMING          # BATCH | STREAMING
    
    # Audio settings
    sample-rate: 16000                  # Sample rate in Hz
    buffer-size: 4096                   # Buffer size in bytes
    
    # VAD settings (batch mode)
    silence-duration-ms: 1500           # Silence duration to trigger processing
    min-chunk-duration-seconds: 2       # Minimum chunk duration
    max-chunk-duration-seconds: 30      # Maximum chunk duration
    
    # VAD configuration
    vad:
      threshold: 0.3                    # Voice activity threshold (0.0-1.0)
      silence-duration-ms: 1500         # Silence duration for VAD
    
    # Debug and development
    debug:
      enabled: false                    # Enable debug mode
      output-path: "./debug/audio"      # Debug output directory
      save-raw-audio: false             # Save raw PCM audio
      save-processed-audio: true        # Save processed audio files
    
    # AssemblyAI configuration
    assemblyai:
      api-key: ${ASSEMBLYAI_API_KEY}    # AssemblyAI API key
      language-code: ENGLISH            # Language code enum
    
    # Deepgram configuration
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}      # Deepgram API key
      language: ENGLISH                 # Language enum
      model: "nova-3"                   # Model name
      punctuate: true                   # Enable punctuation
      interim-results: true             # Enable interim results (streaming)
      detect-language: false            # Auto-detect language
      diarize: false                    # Enable speaker diarization
      
# Logging configuration
logging:
  level:
    ai.driftkit.audio: INFO
    ai.driftkit.audio.engine: DEBUG
    ai.driftkit.audio.converter: DEBUG
```

## API Reference

### SpringAudioSessionManager (Spring Boot)

```java
// Create session with callback
void createSession(String sessionId, Consumer<TranscriptionResult> callback)

// Process audio chunk
void processAudioChunk(String sessionId, byte[] audioData)

// Close session
void closeSession(String sessionId)

// Check session existence
boolean hasSession(String sessionId)

// Get active session count
int getActiveSessionCount()
```

### AudioSessionManager (Core)

```java
// Constructor
AudioSessionManager(CoreAudioConfig config)

// Session management
void createSession(String sessionId, Consumer<TranscriptionResult> callback)
void processAudioChunk(String sessionId, byte[] audioData)
void closeSession(String sessionId)
boolean hasSession(String sessionId)

// Lifecycle
void shutdown()
```

### AudioConverter

```java
// Convert audio to specific format
byte[] convertToFormat(byte[] rawPcmData, int sampleRate, AudioFormatType audioFormat)

// Fast WAV conversion
byte[] convertToWavFast(byte[] rawPcmData, int sampleRate)

// Get conversion capabilities
ConversionInfo getConversionInfo(String format)
PerformanceInfo getPerformanceInfo(String format)
boolean isPureJavaSupported(String format)
```

### TranscriptionResult

```java
// Core content
String getText()                    // Original transcript
String getMergedTranscript()        // Deduplicated segment (streaming)
Double getConfidence()              // Confidence score (0.0-1.0)
String getLanguage()                // Language code

// Status information
boolean isError()                   // Error flag
String getErrorMessage()            // Error description
boolean isInterim()                 // Interim vs final result
Long getTimestamp()                 // Processing timestamp

// Word-level data (streaming mode)
List<WordInfo> getWords()           // Word-level details
Map<String, Object> getMetadata()   // Engine-specific metadata
```

### WordInfo (Streaming Mode)

```java
String getWord()                    // Base word text
String getPunctuatedWord()          // Word with punctuation
Double getStart()                   // Start time (seconds)
Double getEnd()                     // End time (seconds)
Double getConfidence()              // Word confidence
String getLanguage()                // Word language (multilingual)
```

## Error Handling

The library provides comprehensive error handling through the callback mechanism:

```java
sessionManager.createSession(sessionId, result -> {
    if (result.isError()) {
        String error = result.getErrorMessage();
        
        // Handle specific error types
        if (error.contains("API key")) {
            handleAuthenticationError();
        } else if (error.contains("network")) {
            handleNetworkError();
        } else if (error.contains("format")) {
            handleFormatError();
        } else {
            handleGenericError(error);
        }
    } else {
        // Process successful result
        processTranscriptionResult(result);
    }
});
```

## Performance Optimization

### Memory Management

The library implements automatic memory management:

- **Segmented Results**: Streaming mode returns only current segments
- **Automatic Cleanup**: Old words and buffers are cleaned automatically
- **Session Isolation**: Each session has independent memory usage

### Conversion Performance

Audio conversion methods are optimized by preference:

1. **Java Sound API**: Fastest for WAV, AU, AIFF (pure Java)
2. **JAVE Library**: Fast for MP3, OGG, FLAC (embedded FFmpeg)
3. **FFmpeg**: Fallback for all formats (external binary)

### Concurrent Processing

The library supports high-concurrency scenarios:

```java
// Configure for high concurrency
audio:
  processing:
    buffer-size: 2048          # Smaller buffers for faster processing
    silence-duration-ms: 1000  # Shorter silence detection
```

## Building from Source

```bash
# Clone repository
git clone https://github.com/driftkit-ai/driftkit-ai-audio.git
cd driftkit-ai-audio

# Build all modules
mvn clean compile

# Run tests
mvn test

# Package both modules
mvn clean package

# Install to local repository
mvn clean install

# Build only core module
mvn clean package -pl audio-processing-core

# Build only Spring Boot starter
mvn clean package -pl audio-processing-spring-boot-starter
```

## Module Structure

```
driftkit-ai-audio/
├── pom.xml                                    # Parent POM
├── audio-processing-core/                     # Core library
│   ├── pom.xml
│   └── src/main/java/ai/driftkit/audio/
│       ├── core/                             # Core configuration
│       ├── converter/                        # Audio format conversion
│       ├── engine/                           # Transcription engines
│       ├── model/                            # Data models
│       ├── processor/                        # Audio processing
│       └── session/                          # Session management
└── audio-processing-spring-boot-starter/     # Spring Boot integration
    ├── pom.xml
    └── src/main/java/ai/driftkit/audio/
        ├── autoconfigure/                    # Spring auto-configuration
        └── service/                          # Spring service wrappers
```

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or contributions:

- GitHub Issues: [Report bugs or request features](https://github.com/driftkit-ai/driftkit-ai-audio/issues)