package ai.driftkit.audio.service;

import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.config.AudioProcessingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ws.schild.jave.*;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service for converting audio formats using Java libraries and FFmpeg fallback
 */
@Slf4j
@Service
public class AudioConverter {
    
    @Autowired
    private AudioProcessingConfig config;
    
    /**
     * Convert raw PCM audio data to MP3 format using ffmpeg
     */
    public File convertRawToMp3WithFfmpeg(byte[] rawPcmData, String sessionPrefix) throws IOException, InterruptedException {
        File debugDir = new File(config.getDebug().getOutputPath());
        if (!debugDir.exists()) {
            debugDir.mkdirs();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        
        File rawFile = new File(debugDir, sessionPrefix + "temp_raw_" + timestamp + ".pcm");
        File mp3File = new File(debugDir, sessionPrefix + "audio_" + timestamp + ".mp3");
        
        // Write raw PCM data to file
        try (FileOutputStream fos = new FileOutputStream(rawFile)) {
            fos.write(rawPcmData);
        }

        // Build ffmpeg command
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-f", "s16be",
            "-ar", String.valueOf(config.getSampleRate()),
            "-ac", "1",
            "-i", rawFile.getAbsolutePath(),
            "-codec:a", "mp3",
            "-b:a", "64k",  // 64 kbps for smaller file size
            "-ar", "16000", // Keep same sample rate
            mp3File.getAbsolutePath()
        );
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        // Clean up raw file
        if (!rawFile.delete()) {
            // Log warning if needed
        }
        
        if (exitCode == 0 && mp3File.exists()) {
            return mp3File;
        } else {
            throw new IOException("ffmpeg conversion failed with exit code: " + exitCode);
        }
    }
    
    /**
     * Convert raw PCM audio data to specified format using Java libraries.
     * Falls back to FFmpeg if Java conversion fails.
     * 
     * @param rawPcmData Raw PCM audio data
     * @param sampleRate Sample rate of the audio
     * @param format Target format (wav, mp3, etc.)
     * @return Converted audio data
     */
    public byte[] convertToFormat(byte[] rawPcmData, int sampleRate, String format) 
            throws IOException, InterruptedException {
        
        String formatLower = format.toLowerCase();
        
        try {
            // Try Java-based conversion first
            return convertWithJava(rawPcmData, sampleRate, formatLower);
        } catch (Exception e) {
            log.warn("Java-based conversion failed, falling back to FFmpeg", e);
            // Fallback to FFmpeg
            return convertWithFFmpeg(rawPcmData, sampleRate, formatLower);
        }
    }
    
    /**
     * Convert audio using Java libraries (Java Sound API + JAVE).
     */
    private byte[] convertWithJava(byte[] rawPcmData, int sampleRate, String format) 
            throws IOException {
        
        switch (format) {
            case "wav":
                return convertToWav(rawPcmData, sampleRate);
            case "au":
                return convertToAu(rawPcmData, sampleRate);
            case "aiff":
                return convertToAiff(rawPcmData, sampleRate);
            case "mp3":
                return convertWithJave(rawPcmData, sampleRate, format);
            case "ogg":
                return convertWithJave(rawPcmData, sampleRate, format);
            case "flac":
                return convertWithJave(rawPcmData, sampleRate, format);
            default:
                throw new UnsupportedOperationException(
                    "Java conversion not supported for format: " + format);
        }
    }
    
    /**
     * Convert audio using JAVE library.
     */
    private byte[] convertWithJave(byte[] rawPcmData, int sampleRate, String format) 
            throws IOException {
        
        Path tempDir = Files.createTempDirectory("jave-conversion");
        Path inputWav = tempDir.resolve("input.wav");
        Path outputFile = tempDir.resolve("output." + format);
        
        try {
            // First convert raw PCM to WAV (JAVE input format)
            byte[] wavData = convertToWav(rawPcmData, sampleRate);
            Files.write(inputWav, wavData);
            
            // Set up JAVE conversion
            MultimediaObject source = new MultimediaObject(inputWav.toFile());
            
            // Configure audio attributes based on format
            AudioAttributes audioAttributes = new AudioAttributes();
            audioAttributes.setSamplingRate(sampleRate);
            audioAttributes.setChannels(1); // Mono
            
            switch (format.toLowerCase()) {
                case "mp3":
                    audioAttributes.setCodec("libmp3lame");
                    audioAttributes.setBitRate(64000); // 64 kbps
                    break;
                case "ogg":
                    audioAttributes.setCodec("libvorbis");
                    audioAttributes.setBitRate(128000); // 128 kbps
                    break;
                case "flac":
                    audioAttributes.setCodec("flac");
                    // No bitrate for lossless
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported format for JAVE: " + format);
            }
            
            // Set encoding attributes
            EncodingAttributes encodingAttributes = new EncodingAttributes();
            encodingAttributes.setInputFormat("wav");
            encodingAttributes.setOutputFormat(format);
            encodingAttributes.setAudioAttributes(audioAttributes);
            
            // Perform conversion
            Encoder encoder = new Encoder();
            encoder.encode(source, outputFile.toFile(), encodingAttributes);
            
            if (!Files.exists(outputFile)) {
                throw new IOException("JAVE conversion failed - output file not created");
            }
            
            return Files.readAllBytes(outputFile);
            
        } catch (EncoderException e) {
            throw new IOException("JAVE encoding failed", e);
        } finally {
            // Clean up temporary files
            try {
                Files.deleteIfExists(inputWav);
                Files.deleteIfExists(outputFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                log.warn("Failed to clean up JAVE temporary files", e);
            }
        }
    }
    
    /**
     * Convert raw PCM to WAV format using Java Sound API.
     */
    private byte[] convertToWav(byte[] rawPcmData, int sampleRate) throws IOException {
        // Create audio format
        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,  // bits per sample
            1,   // channels (mono)
            2,   // frame size (16 bits = 2 bytes)
            sampleRate,
            false // little endian
        );
        
        // Create audio input stream from raw data
        ByteArrayInputStream rawInputStream = new ByteArrayInputStream(rawPcmData);
        AudioInputStream audioInputStream = new AudioInputStream(
            rawInputStream, audioFormat, rawPcmData.length / audioFormat.getFrameSize());
        
        // Convert to WAV format
        ByteArrayOutputStream wavOutputStream = new ByteArrayOutputStream();
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavOutputStream);
        
        audioInputStream.close();
        
        return wavOutputStream.toByteArray();
    }
    
    /**
     * Convert raw PCM to AU format using Java Sound API.
     */
    private byte[] convertToAu(byte[] rawPcmData, int sampleRate) throws IOException {
        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, true // big endian for AU
        );
        
        ByteArrayInputStream rawInputStream = new ByteArrayInputStream(rawPcmData);
        AudioInputStream audioInputStream = new AudioInputStream(
            rawInputStream, audioFormat, rawPcmData.length / audioFormat.getFrameSize());
        
        ByteArrayOutputStream auOutputStream = new ByteArrayOutputStream();
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.AU, auOutputStream);
        
        audioInputStream.close();
        
        return auOutputStream.toByteArray();
    }
    
    /**
     * Convert raw PCM to AIFF format using Java Sound API.
     */
    private byte[] convertToAiff(byte[] rawPcmData, int sampleRate) throws IOException {
        AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, true // big endian for AIFF
        );
        
        ByteArrayInputStream rawInputStream = new ByteArrayInputStream(rawPcmData);
        AudioInputStream audioInputStream = new AudioInputStream(
            rawInputStream, audioFormat, rawPcmData.length / audioFormat.getFrameSize());
        
        ByteArrayOutputStream aiffOutputStream = new ByteArrayOutputStream();
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.AIFF, aiffOutputStream);
        
        audioInputStream.close();
        
        return aiffOutputStream.toByteArray();
    }
    
    /**
     * Fallback conversion using FFmpeg for formats not supported by Java.
     */
    private byte[] convertWithFFmpeg(byte[] rawPcmData, int sampleRate, String format) 
            throws IOException, InterruptedException {
        
        Path tempDir = Files.createTempDirectory("audio-conversion");
        Path rawFile = tempDir.resolve("input.pcm");
        Path convertedFile = tempDir.resolve("output." + format);
        
        try {
            // Write raw PCM data to temporary file
            Files.write(rawFile, rawPcmData);
            
            // Build ffmpeg command based on format
            ProcessBuilder pb = buildFFmpegCommand(
                rawFile.toString(), 
                convertedFile.toString(), 
                sampleRate, 
                format
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new IOException("FFmpeg conversion failed with exit code: " + exitCode);
            }
            
            if (!Files.exists(convertedFile)) {
                throw new IOException("Converted audio file was not created");
            }
            
            return Files.readAllBytes(convertedFile);
            
        } finally {
            // Clean up temporary files
            try {
                Files.deleteIfExists(rawFile);
                Files.deleteIfExists(convertedFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                log.warn("Failed to clean up temporary files", e);
            }
        }
    }
    
    private ProcessBuilder buildFFmpegCommand(
            String inputFile, 
            String outputFile, 
            int sampleRate, 
            String format) {
        
        return switch (format.toLowerCase()) {
            case "wav" -> new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "s16le",  // Little-endian 16-bit PCM
                "-ar", String.valueOf(sampleRate),
                "-ac", "1",     // Mono
                "-i", inputFile,
                "-f", "wav",
                outputFile
            );
            
            case "mp3" -> new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "s16le",
                "-ar", String.valueOf(sampleRate),
                "-ac", "1",
                "-i", inputFile,
                "-codec:a", "mp3",
                "-b:a", "64k",
                outputFile
            );
            
            case "flac" -> new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "s16le",
                "-ar", String.valueOf(sampleRate),
                "-ac", "1",
                "-i", inputFile,
                "-codec:a", "flac",
                outputFile
            );
            
            case "ogg" -> new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "s16le",
                "-ar", String.valueOf(sampleRate),
                "-ac", "1",
                "-i", inputFile,
                "-codec:a", "libvorbis",
                "-b:a", "128k",
                outputFile
            );
            
            default -> throw new IllegalArgumentException("Unsupported audio format: " + format);
        };
    }
    
    /**
     * Get available conversion methods for a format.
     * 
     * @param format Audio format
     * @return Information about conversion method
     */
    public ConversionInfo getConversionInfo(String format) {
        String formatLower = format.toLowerCase();
        
        boolean javaSupported = switch (formatLower) {
            case "wav", "au", "aiff" -> true; // Java Sound API
            case "mp3", "ogg", "flac" -> true; // JAVE library
            default -> false;
        };
        
        boolean ffmpegSupported = switch (formatLower) {
            case "wav", "mp3", "flac", "ogg", "aac", "m4a" -> true;
            default -> false;
        };
        
        return new ConversionInfo(formatLower, javaSupported, ffmpegSupported);
    }
    
    /**
     * Information about conversion capabilities for a format.
     */
    public static class ConversionInfo {
        private final String format;
        private final boolean javaSupported;
        private final boolean ffmpegSupported;
        
        public ConversionInfo(String format, boolean javaSupported, boolean ffmpegSupported) {
            this.format = format;
            this.javaSupported = javaSupported;
            this.ffmpegSupported = ffmpegSupported;
        }
        
        public String getFormat() { return format; }
        public boolean isJavaSupported() { return javaSupported; }
        public boolean isFFmpegSupported() { return ffmpegSupported; }
        public boolean isSupported() { return javaSupported || ffmpegSupported; }
        
        public String getPreferredMethod() {
            if (javaSupported) return "Java libraries (JAVE/Sound API)";
            if (ffmpegSupported) return "FFmpeg";
            return "Not supported";
        }
        
        @Override
        public String toString() {
            return String.format("ConversionInfo{format='%s', java=%s, ffmpeg=%s, preferred='%s'}", 
                format, javaSupported, ffmpegSupported, getPreferredMethod());
        }
    }
    
    /**
     * Fast conversion to WAV format optimized for memory usage.
     * This method is optimized for real-time processing.
     */
    public byte[] convertToWavFast(byte[] rawPcmData, int sampleRate) {
        try {
            return convertToWav(rawPcmData, sampleRate);
        } catch (IOException e) {
            log.error("Fast WAV conversion failed", e);
            throw new RuntimeException("WAV conversion failed", e);
        }
    }
    
    /**
     * Check if a format can be converted purely in Java without external dependencies.
     */
    public boolean isPureJavaSupported(String format) {
        return switch (format.toLowerCase()) {
            case "wav", "au", "aiff" -> true; // Pure Java Sound API
            default -> false; // JAVE and FFmpeg require native binaries
        };
    }
    
    /**
     * Get performance characteristics for a conversion method.
     */
    public PerformanceInfo getPerformanceInfo(String format) {
        ConversionInfo conversionInfo = getConversionInfo(format);
        
        if (conversionInfo.isJavaSupported()) {
            boolean pureJava = isPureJavaSupported(format);
            return new PerformanceInfo(
                pureJava ? "Fastest" : "Fast", 
                pureJava ? "Very Low" : "Low",
                pureJava ? "None" : "Native libraries required"
            );
        } else if (conversionInfo.isFFmpegSupported()) {
            return new PerformanceInfo("Slower", "High", "FFmpeg binary required");
        } else {
            return new PerformanceInfo("Not supported", "N/A", "Format not supported");
        }
    }
    
    /**
     * Performance characteristics for conversion methods.
     */
    public static class PerformanceInfo {
        private final String speed;
        private final String resourceUsage;
        private final String dependencies;
        
        public PerformanceInfo(String speed, String resourceUsage, String dependencies) {
            this.speed = speed;
            this.resourceUsage = resourceUsage;
            this.dependencies = dependencies;
        }
        
        public String getSpeed() { return speed; }
        public String getResourceUsage() { return resourceUsage; }
        public String getDependencies() { return dependencies; }
        
        @Override
        public String toString() {
            return String.format("Performance{speed='%s', resources='%s', deps='%s'}", 
                speed, resourceUsage, dependencies);
        }
    }
}