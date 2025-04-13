# Android Intent API Documentation

## Introduction

The Android Intent API provides a powerful mechanism for inter-component communication in Android applications. This document details the TypeScript interface for working with Android Intents through a JavaScript bridge.

An Intent in Android is an abstract description of an operation to be performed. It can be used to launch activities, services, broadcast receivers, and more. The Intent API provided here allows you to create and manipulate Intents programmatically from JavaScript.

## Intent Class

The `Intent` class is the core of the Android Intent system, allowing you to create and manipulate intent objects.

### Constructor

```typescript
constructor(action?: string | IntentAction | null);
```

Create a new Intent, optionally specifying an action.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `action` | `string \| null` | The action for this intent |
| `packageName` | `string \| null` | The package name for this intent |
| `component` | `string \| null` | The component for this intent |
| `extras` | `Record<string, any>` | Extra data passed with the intent |
| `flags` | `string[]` | The flags set on this intent |
| `categories` | `string[]` | The categories added to this intent |
| `executor` | `AdbExecutor` | The ADB executor for this intent |

### Methods

#### Setting Intent Components

```typescript
setComponent(packageName: string, component: string): Intent;
```
Set the specific component for this intent by package name and component name.

```typescript
setPackage(packageName: string): Intent;
```
Set just the package name without specifying a component.

#### Intent Actions and Categories

```typescript
setAction(action: string | IntentAction): Intent;
```
Set the action for this intent (e.g., `ACTION_MAIN`, `ACTION_VIEW`).

```typescript
addCategory(category: string | IntentCategory): Intent;
```
Add a category to this intent (e.g., `CATEGORY_LAUNCHER`, `CATEGORY_HOME`).

```typescript
removeCategory(category: string | IntentCategory): Intent;
```
Remove a category from this intent.

```typescript
hasCategory(category: string | IntentCategory): boolean;
```
Check if the intent has a specific category.

```typescript
getCategories(): string[];
```
Get all categories added to this intent.

```typescript
clearCategories(): Intent;
```
Remove all categories from this intent.

#### Flags and Extras

```typescript
addFlag(flag: IntentFlag | string): Intent;
```
Add a flag to this intent (e.g., `FLAG_ACTIVITY_NEW_TASK`).

```typescript
putExtra(key: string, value: any): Intent;
```
Add extra data to this intent, with a key-value pair.

#### Intent Execution

```typescript
start(): Promise<string>;
```
Start this intent as an activity and return the command output.

```typescript
sendBroadcast(): Promise<string>;
```
Send this intent as a broadcast and return the command output.

```typescript
startService(): Promise<string>;
```
Start this intent as a service and return the command output.

## Intent Actions (IntentAction)

Intent actions define the operation to be performed. Here are some of the most commonly used actions:

| Action | Value | Description |
|--------|-------|-------------|
| `ACTION_MAIN` | "android.intent.action.MAIN" | Primary entry point to an application |
| `ACTION_VIEW` | "android.intent.action.VIEW" | Display data to the user |
| `ACTION_SEND` | "android.intent.action.SEND" | Send data to someone else |
| `ACTION_EDIT` | "android.intent.action.EDIT" | Edit an existing item |
| `ACTION_DIAL` | "android.intent.action.DIAL" | Dial a number |
| `ACTION_CALL` | "android.intent.action.CALL" | Perform a call to someone |
| `ACTION_SEARCH` | "android.intent.action.SEARCH" | Search for data |
| `ACTION_GET_CONTENT` | "android.intent.action.GET_CONTENT" | Allow the user to select data |

### System Actions

| Action | Description |
|--------|-------------|
| `ACTION_SETTINGS` | Open system settings |
| `ACTION_POWER_USAGE_SUMMARY` | Show power usage details |
| `ACTION_WIRELESS_SETTINGS` | Open wireless settings |
| `ACTION_DEVICE_INFO_SETTINGS` | Show device information |

### Media Actions

| Action | Description |
|--------|-------------|
| `ACTION_MEDIA_PLAY` | Play media |
| `ACTION_MEDIA_PAUSE` | Pause media |
| `ACTION_MEDIA_STOP` | Stop media |
| `ACTION_IMAGE_CAPTURE` | Capture an image |
| `ACTION_VIDEO_CAPTURE` | Capture a video |

### Broadcast Actions

| Action | Description |
|--------|-------------|
| `ACTION_BOOT_COMPLETED` | Broadcast after system boot |
| `ACTION_BATTERY_LOW` | Broadcast when battery is low |
| `ACTION_SCREEN_ON` | Broadcast when screen turns on |
| `ACTION_SCREEN_OFF` | Broadcast when screen turns off |
| `ACTION_POWER_CONNECTED` | Broadcast when power is connected |

## Intent Categories (IntentCategory)

Intent categories provide additional information about the components that can handle an intent. Common categories include:

| Category | Value | Description |
|----------|-------|-------------|
| `CATEGORY_DEFAULT` | "android.intent.category.DEFAULT" | Default category for implicit intents |
| `CATEGORY_LAUNCHER` | "android.intent.category.LAUNCHER" | Activities that can be the initial activity of a task |
| `CATEGORY_HOME` | "android.intent.category.HOME" | Activity is a home screen |
| `CATEGORY_BROWSABLE` | "android.intent.category.BROWSABLE" | Activities that can be safely invoked from a browser |
| `CATEGORY_APP_BROWSER` | "android.intent.category.APP_BROWSER" | Activity is a web browser |
| `CATEGORY_APP_CALCULATOR` | "android.intent.category.APP_CALCULATOR" | Activity is a calculator |
| `CATEGORY_APP_CALENDAR` | "android.intent.category.APP_CALENDAR" | Activity is a calendar |
| `CATEGORY_APP_MAPS` | "android.intent.category.APP_MAPS" | Activity is a map application |

## Intent Flags (IntentFlag)

Intent flags modify the default behavior of an intent. Common flags include:

### Activity Launch Flags

| Flag | Value | Description |
|------|-------|-------------|
| `ACTIVITY_NEW_TASK` | "0x10000000" | Start activity in a new task |
| `ACTIVITY_CLEAR_TOP` | "0x04000000" | Clear all activities on top of the destination |
| `ACTIVITY_SINGLE_TOP` | "0x20000000" | Don't launch a new activity if the current activity is the same |
| `ACTIVITY_CLEAR_TASK` | "0x00008000" | Clear all existing tasks when launching |
| `ACTIVITY_NO_HISTORY` | "0x40000000" | Activity won't remain in history stack |

### URI Permission Flags

| Flag | Description |
|------|-------------|
| `GRANT_READ_URI_PERMISSION` | Grant read permission for a content URI |
| `GRANT_WRITE_URI_PERMISSION` | Grant write permission for a content URI |

### Package Visibility Flags

| Flag | Description |
|------|-------------|
| `FLAG_EXCLUDE_STOPPED_PACKAGES` | Don't deliver to stopped packages |
| `FLAG_INCLUDE_STOPPED_PACKAGES` | Include stopped packages in broadcasts |

## Usage Examples

### Example 1: Launching an Application

```typescript
// Create an intent to launch an application
const intent = new Intent(IntentAction.ACTION_MAIN);
intent.addCategory(IntentCategory.CATEGORY_LAUNCHER);
intent.setPackage("com.example.app");
intent.addFlag(IntentFlag.ACTIVITY_NEW_TASK);
await intent.start();
```

### Example 2: Opening a Web URL

```typescript
// Create an intent to open a URL in a browser
const intent = new Intent(IntentAction.ACTION_VIEW);
intent.addCategory(IntentCategory.CATEGORY_BROWSABLE);
intent.putExtra("url", "https://www.example.com");
await intent.start();
```

### Example 3: Sending a Text Message

```typescript
// Create an intent to send a text message
const intent = new Intent(IntentAction.ACTION_SEND);
intent.putExtra("android.intent.extra.TEXT", "Hello, this is a test message");
intent.putExtra("android.intent.extra.SUBJECT", "Test Subject");
await intent.start();
```

### Example 4: Starting a Specific Component

```typescript
// Create an intent to start a specific component
const intent = new Intent();
intent.setComponent("com.example.app", "com.example.app.MainActivity");
await intent.start();
```

### Example 5: Using Type-Safe Enums

```typescript
// Using the type-safe enums for actions, categories, and flags
const intent = new Intent(IntentAction.ACTION_MAIN);
intent.addCategory(IntentCategory.CATEGORY_LAUNCHER);
intent.addFlag(IntentFlag.ACTIVITY_NEW_TASK);
intent.addFlag(IntentFlag.ACTIVITY_CLEAR_TOP);
await intent.start();
```

## Best Practices

1. **Always set an action**: Most intents should have a clear action that describes what you want to do.

2. **Use categories appropriately**: Categories help the system determine which components can handle your intent.

3. **Consider adding FLAGS**: Flags like `ACTIVITY_NEW_TASK` are often necessary for proper intent behavior.

4. **Add the DEFAULT category for implicit intents**: Activities that handle implicit intents should include the `CATEGORY_DEFAULT` category.

5. **Use explicit intents for application components**: When targeting a specific component in your application, use explicit intents with `setComponent()`.

6. **Check operation results**: Always check the Promise result to see if the operation succeeded.

## Common Issues and Troubleshooting

1. **Activity not found**: Make sure the package name and component name are correct.

2. **Permission denied**: Some intents require specific permissions.

3. **Missing required flags**: Some operations require specific flags to be set.

4. **Missing action**: Make sure to set an appropriate action for the intent.

5. **Intent resolution failed**: For implicit intents, there might be no matching component.

## References

- [Android Intent Documentation](https://developer.android.com/reference/android/content/Intent)
- [Android Intent Flags](https://developer.android.com/reference/android/content/Intent#constants_1)
- [Common Intents](https://developer.android.com/guide/components/intents-common) 