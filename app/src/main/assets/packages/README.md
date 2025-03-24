# HJSON Tool Packages

This directory contains tool packages in HJSON format. HJSON is a human-friendly JSON format that supports comments, multi-line strings, and relaxed syntax.

## Package Format

A tool package is defined in HJSON format with the following structure:

```hjson
{
  // Package metadata
  name: package_name              // Required: Unique package name
  description: Package description // Required: Description of the package
  
  // List of tools in this package
  tools: [
    {
      name: tool_name             // Required: Tool name
      description: Tool description // Required: Description of what the tool does
      
      // Optional: Parameters for the tool
      parameters: [
        {
          name: parameter_name    // Parameter name
          description: Parameter description // Parameter description
          type: string            // Parameter type (string, number, boolean, object, array)
          required: true          // Whether the parameter is required
        }
      ]
      
      // Required: JavaScript code that implements the tool
      script: '''
        // JavaScript code goes here
        // Use params object to access parameters
        // Use complete() function to return results
        
        const result = "Your result here";
        complete(result);
      '''
    }
  ]
  
  // Required: Tool category
  // Valid categories: SYSTEM_OPERATION, NETWORK, FILE_OPERATION, MEDIA, UTILITY, USER_INTERFACE, DEVELOPMENT
  category: SYSTEM_OPERATION
}
```

## Features

- Supports comments using `//` or `/* */`
- Multi-line strings using triple quotes `'''`
- No need for quotes around keys
- No need for commas between elements
- More forgiving of syntax errors

## Creating New Packages

1. Create a new .hjson file in this directory
2. Follow the format above
3. The file name should match the package name (e.g., `package_name.hjson`)
4. Implement your tool logic in JavaScript in the script property

## Using Tool Packages

Tool packages are automatically loaded from this directory when the app starts. You can then invoke them using the format `package_name:tool_name` in your AI conversations.

## Example

See the `daily_life.hjson` and `javascript_tester.hjson` files for complete examples. 