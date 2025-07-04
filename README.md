# DriftKit AI Audio

A Java Spring Boot library for real-time audio processing with voice activity detection (VAD) and speech-to-text transcription. The library supports multiple transcription engines (AssemblyAI and Deepgram) with both batch and streaming processing modes.

## Features

- **Real-time Audio Processing**: Process audio streams in real-time with low latency
- **Voice Activity Detection (VAD)**: Intelligent detection of speech segments
- **Multiple Transcription Engines**:
  - AssemblyAI (batch mode)
  - Deepgram (batch and streaming modes)
- **Processing Modes**:
  - Batch: VAD-based chunking with complete segment transcription
  - Streaming: Real-time transcription with word-level timing
- **Multi-language Support**: Type-safe language codes for 30+ languages
- **Session Isolation**: Process multiple audio streams concurrently
- **Audio Format Conversion**: Support for various audio formats (WAV, MP3, OGG, FLAC)
- **Memory Efficient**: Automatic cleanup and segmented results prevent memory overflow

## Installation

### Maven
```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-ai-audio</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle
```gradle
implementation 'ai.driftkit:driftkit-ai-audio:1.0.0-SNAPSHOT'
```

## Requirements

- Java 17+
- Maven 3.6+
- Spring Boot 3.x
- FFmpeg (optional, for extended audio format support)

## Quick Start

### 1. Basic Configuration

Add to your `application.yml`:

```yaml
audio:
  processing:
    engine: DEEPGRAM
    processing-mode: BATCH
    sample-rate: 16000
    buffer-size: 4096
    
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}
      language: ENGLISH
      model: "nova-2"
      punctuate: true
```

### 2. Basic Usage

```java
@Component
public class AudioTranscriptionService {
    
    @Autowired
    private AudioSessionManager sessionManager;
    
    public void transcribeAudio(String userId, byte[] audioData) {
        // Create session with callback
        sessionManager.createSession(userId, result -> {
            if (!result.isError()) {
                System.out.println("Transcription: " + result.getText());
            } else {
                System.err.println("Error: " + result.getErrorMessage());
            }
        });
        
        // Process audio
        sessionManager.processAudioChunk(userId, audioData);
        
        // Close session when done
        sessionManager.closeSession(userId);
    }
}
```

## Configuration Options

### Engine Types
- `ASSEMBLYAI` - AssemblyAI transcription service
- `DEEPGRAM` - Deepgram transcription service

### Processing Modes
- `BATCH` - VAD-based chunking with complete segment transcription
- `STREAMING` - Real-time streaming transcription (Deepgram only)

### Supported Languages

The library supports 30+ languages with type-safe enum values:

```java
LanguageCode.ENGLISH
LanguageCode.SPANISH
LanguageCode.FRENCH
LanguageCode.GERMAN
LanguageCode.CHINESE_SIMPLIFIED
// ... and many more
```

## Usage Examples

### Example 1: Batch Mode with VAD

```java
@Component
public class BatchTranscriptionExample {
    
    @Autowired
    private AudioSessionManager sessionManager;
    
    public void transcribeSpeech() {
        String sessionId = "user-123";
        
        // Create session
        sessionManager.createSession(sessionId, result -> {
            if (!result.isError()) {
                System.out.println("Speech segment: " + result.getText());
                System.out.println("Confidence: " + result.getConfidence());
            }
        });
        
        // Simulate audio streaming
        byte[] audioChunk = captureAudioFromMicrophone();
        while (isRecording()) {
            sessionManager.processAudioChunk(sessionId, audioChunk);
            audioChunk = captureAudioFromMicrophone();
        }
        
        // Close session
        sessionManager.closeSession(sessionId);
    }
}
```

### Example 2: Real-time Streaming with Interim Results

```java
@Component
public class StreamingTranscriptionExample {
    
    @Autowired
    private AudioSessionManager sessionManager;
    
    public void streamTranscription() {
        String sessionId = "stream-user-456";
        
        sessionManager.createSession(sessionId, result -> {
            if (result.isError()) {
                log.error("Error: {}", result.getErrorMessage());
                return;
            }
            
            if (result.isInterim()) {
                // Live transcription update
                System.out.println("[LIVE] " + result.getMergedTranscript());
                
                // Access word-level timing
                for (WordInfo word : result.getWords()) {
                    System.out.printf("%s [%.1f-%.1fs] ", 
                        word.getPunctuatedWord(), 
                        word.getStart(), 
                        word.getEnd());
                }
            } else {
                // Final transcription segment
                System.out.println("[FINAL] " + result.getMergedTranscript());
                saveToDatabase(result.getMergedTranscript());
            }
        });
        
        // Stream audio
        streamAudioChunks(sessionId);
        
        sessionManager.closeSession(sessionId);
    }
}
```

### Example 3: Multi-language Support

```java
@Component
public class MultiLanguageExample {
    
    @Autowired
    private AudioSessionManager sessionManager;
    
    public void transcribeInSpanish() {
        // Configure for Spanish
        AudioProcessingConfig config = new AudioProcessingConfig();
        config.setEngine(EngineType.DEEPGRAM);
        config.setProcessingMode(ProcessingMode.BATCH);
        
        DeepgramConfig deepgramConfig = new DeepgramConfig();
        deepgramConfig.setLanguage(LanguageCode.MULTI);
        config.setDeepgram(deepgramConfig);
        
        // Create session with language override
        sessionManager.createSession("spanish-user", result -> {
            System.out.println("Transcripción: " + result.getText());
        });
    }
}
```

### Example 4: Concurrent Session Processing

```java
@Component
public class ConcurrentSessionExample {
    
    @Autowired
    private AudioSessionManager sessionManager;
    
    public void processConcurrentSessions() {
        // Create multiple sessions
        for (int i = 0; i < 5; i++) {
            String sessionId = "user-" + i;
            
            sessionManager.createSession(sessionId, result -> {
                System.out.println(sessionId + ": " + result.getText());
            });
        }
        
        // Process audio for each session independently
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        for (int i = 0; i < 5; i++) {
            final String sessionId = "user-" + i;
            executor.submit(() -> {
                byte[] audio = getAudioForUser(sessionId);
                sessionManager.processAudioChunk(sessionId, audio);
            });
        }
    }
}
```

### Example 5: Microphone Capture

```java
@Component
public class MicrophoneCaptureExample {
    
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    
    @Autowired
    private AudioSessionManager sessionManager;
    
    public void captureMicrophone() throws Exception {
        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false
        );
        
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(audioFormat, BUFFER_SIZE);
        microphone.start();
        
        String sessionId = "mic-session";
        sessionManager.createSession(sessionId, result -> {
            if (!result.isError()) {
                System.out.println("Heard: " + result.getText());
            }
        });
        
        byte[] buffer = new byte[BUFFER_SIZE];
        while (isListening()) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                byte[] audioChunk = Arrays.copyOf(buffer, bytesRead);
                sessionManager.processAudioChunk(sessionId, audioChunk);
            }
        }
        
        microphone.stop();
        microphone.close();
        sessionManager.closeSession(sessionId);
    }
}
```

## Complete Configuration Example

```yaml
audio:
  processing:
    # Engine selection
    engine: DEEPGRAM
    processing-mode: STREAMING
    
    # Audio settings
    sample-rate: 16000
    buffer-size: 4096
    
    # VAD settings (for batch mode)
    silence-duration-ms: 1500
    min-chunk-duration-seconds: 2
    max-chunk-duration-seconds: 30
    
    vad:
      threshold: 0.3
      silence-duration-ms: 1500
    
    # Debug settings
    debug:
      enabled: true
      output-path: "./debug/audio"
      save-raw-audio: true
      save-processed-audio: true
    
    # Engine configurations
    assemblyai:
      api-key: ${ASSEMBLYAI_API_KEY}
      language-code: ENGLISH
    
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}
      language: ENGLISH
      model: "nova-3"
      punctuate: true
      interim-results: true
      detect-language: true
      diarize: false

# Spring configuration
spring:
  main:
    allow-circular-references: true

# Logging
logging:
  level:
    ai.driftkit.audio: INFO
    ai.driftkit.audio.engine: DEBUG
```

## API Reference

### AudioSessionManager

The main entry point for managing audio transcription sessions.

```java
// Create a new session
createSession(String sessionId, Consumer<TranscriptionResult> callback)

// Process audio data
processAudioChunk(String sessionId, byte[] audioData)

// Close a session
closeSession(String sessionId)

// Check if session exists
hasSession(String sessionId)
```

### TranscriptionResult

The result object returned in callbacks:

```java
// Core fields
String getText()                  // Transcribed text
String getMergedTranscript()      // Deduplicated segment text (streaming)
Double getConfidence()            // Confidence score (0.0 to 1.0)
String getLanguage()              // Detected/configured language

// Status fields
boolean isError()                 // Error indicator
String getErrorMessage()          // Error description
boolean isInterim()              // Interim vs final result

// Word-level data (streaming mode)
List<WordInfo> getWords()        // Detailed word information
```

### WordInfo

Individual word metadata (streaming mode):

```java
String getWord()                 // Base word text
String getPunctuatedWord()       // Word with punctuation
Double getStart()                // Start time in seconds
Double getEnd()                  // End time in seconds
Double getConfidence()           // Word confidence score
```

## Debugging

Enable debug mode to save audio files:

```yaml
audio:
  processing:
    debug:
      enabled: true
      output-path: "./debug/audio"
      save-raw-audio: true
      save-processed-audio: true
```

## Error Handling

All errors are returned through the callback mechanism:

```java
sessionManager.createSession(sessionId, result -> {
    if (result.isError()) {
        log.error("Transcription error: {}", result.getErrorMessage());
        // Handle error appropriately
    } else {
        // Process successful result
    }
});
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/driftkit-ai-audio.git
cd driftkit-ai-audio

# Build the project
mvn clean compile

# Run tests
mvn test

# Package the library
mvn clean package

# Install to local repository
mvn clean install
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or contributions, please visit the [GitHub repository](https://github.com/yourusername/driftkit-ai-audio).