# FFmpeg Tools Documentation

This document describes the FFmpeg tools available in the Assistance Package.

## Overview

The FFmpeg tools provide functionality for video and audio processing using FFmpeg. These tools allow you to execute custom FFmpeg commands, get system information, and perform video conversions with simplified parameters.

## Available Tools

### 1. FFmpeg Execute

Executes a custom FFmpeg command.

```typescript
await Tools.FFmpeg.execute(command: string): Promise<FFmpegResultData>
```

**Parameters:**
- `command`: The FFmpeg command to execute

**Example:**
```javascript
const result = await Tools.FFmpeg.execute("-i input.mp4 -c:v libx264 output.mp4");
```

### 2. FFmpeg Info

Retrieves FFmpeg system information including version, build configuration, and supported codecs.

```typescript
await Tools.FFmpeg.info(): Promise<FFmpegResultData>
```

**Example:**
```javascript
const info = await Tools.FFmpeg.info();
console.log(info.output); // Displays FFmpeg version and configuration
```

### 3. FFmpeg Convert

Converts video files with simplified parameters.

```typescript
await Tools.FFmpeg.convert(
    inputPath: string,
    outputPath: string,
    options?: {
        video_codec?: string;
        audio_codec?: string;
        resolution?: string;
        bitrate?: string;
    }
): Promise<FFmpegResultData>
```

**Parameters:**
- `inputPath`: Source video file path
- `outputPath`: Destination video file path
- `options`: Optional conversion parameters
  - `video_codec`: Video codec to use (e.g., "libx264")
  - `audio_codec`: Audio codec to use (e.g., "aac")
  - `resolution`: Output resolution (e.g., "1280x720")
  - `bitrate`: Video bitrate (e.g., "1000k")

**Example:**
```javascript
const result = await Tools.FFmpeg.convert(
    "/storage/emulated/0/Movies/input.mp4",
    "/storage/emulated/0/Movies/output.mp4",
    {
        video_codec: "libx264",
        audio_codec: "aac",
        resolution: "1280x720",
        bitrate: "1000k"
    }
);
```

## Result Data Structure

All FFmpeg tools return a `FFmpegResultData` object with the following structure:

```typescript
interface FFmpegResultData {
    command: string;        // The executed command
    returnCode: number;     // FFmpeg return code
    output: string;         // Command output
    duration: number;       // Execution duration in milliseconds
    videoStreams: FFmpegStreamInfo[];  // Video stream information
    audioStreams: FFmpegStreamInfo[];  // Audio stream information
}

interface FFmpegStreamInfo {
    index: number;          // Stream index
    type: string;          // Stream type (video/audio)
    codec: string;         // Codec name
    frameRate?: string;    // Video frame rate
    sampleRate?: string;   // Audio sample rate
    channels?: number;     // Number of audio channels
}
```

## Error Handling

All FFmpeg tools return a Promise that resolves to a `FFmpegResultData` object. If an error occurs, the Promise will be rejected with an error message.

**Example:**
```javascript
try {
    const result = await Tools.FFmpeg.convert("input.mp4", "output.mp4");
    console.log("Conversion successful:", result);
} catch (error) {
    console.error("Conversion failed:", error);
}
```

## Best Practices

1. Always check the `returnCode` in the result to ensure the operation was successful.
2. Use the `output` field to get detailed information about the operation.
3. For video conversions, prefer using the `convert` method over `execute` for better compatibility and simpler parameter handling.
4. When using `execute`, make sure to properly escape file paths and parameters.
5. Monitor the `duration` field to track operation performance.

## Examples

### Basic Video Conversion
```javascript
const result = await Tools.FFmpeg.convert(
    "input.mp4",
    "output.mp4",
    {
        video_codec: "libx264",
        resolution: "1280x720"
    }
);
```

### Get FFmpeg Information
```javascript
const info = await Tools.FFmpeg.info();
console.log("FFmpeg Version:", info.output);
```

### Custom FFmpeg Command
```javascript
const result = await Tools.FFmpeg.execute(
    "-i input.mp4 -c:v libx264 -preset medium -crf 23 output.mp4"
);
```

### Error Handling Example
```javascript
try {
    const result = await Tools.FFmpeg.convert("input.mp4", "output.mp4");
    if (result.returnCode === 0) {
        console.log("Conversion successful");
    } else {
        console.error("Conversion failed with code:", result.returnCode);
        console.error("Error output:", result.output);
    }
} catch (error) {
    console.error("Operation failed:", error);
}
``` 