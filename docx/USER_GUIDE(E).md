# Operit AI User Guide

<div align="center">
  <a href="./USER_GUIDE.md">简体中文</a> | <strong>English</strong>
</div>

<p align="center">
  <img src="../app/src/main/res/playstore-icon.png" width="120" height="120" alt="Operit Logo">
</p>
<p align="center">
  <strong>From here, you will witness the creativity of countless users.</strong><br>
  <strong>From here, you will showcase your creativity!</strong>
</p>

---

<h2 id="table-of-contents">📖 Table of Contents</h2>

- [Introduction](#introduction)
- [Basic Walkthrough](#basic-walkthrough)
  - [First Use/Trial](#first-use)
  - [How to Package a Web App Created by AI](#package-web-app)
  - [How to Configure Your Own API/Other Models](#api-configuration)
    - [Configure Your Own DeepSeek API](#deepseek-api)
    - [Switch to Other AI Models](#switch-models)
  - [Shizuku Authorization Flow](#shizuku-authorization)
  - [Package Management and MCP Usage](#package-management)
    - [Enabling Packages](#enabling-packages)
    - [One-Click Environment Configuration](#environment-configuration)
    - [MCP Configuration Flow](#mcp-configuration)
- [Advanced Usage in Practice](#advanced-usage-in-practice)
  - [Out-of-the-Box](#out-of-the-box)
  - [Extension Packages](#extension-packages)
  - [Core Tools](#core-tools)
  - [MCP Market](#mcp-market)
- [Frequently Asked Questions (FAQ)](#faq)
  - [MCP Troubleshooting](#mcp-troubleshooting)
- [Join Us](#join-us)
- [Wishlist](#wishlist)
- [Reminiscence (Older Version FAQ)](#reminiscence)

---

<h2 id="introduction" style="display: flex; justify-content: space-between; align-items: center;"><span>✨ Introduction</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

Welcome to **Operit AI**! This guide is designed to help you get started quickly and make the most of Operit AI's powerful features, turning your phone into a true smart assistant.

>*This document was last updated: 2025/6/15*

<div STYLE="page-break-after: always;"></div>

<h2 id="basic-walkthrough" style="display: flex; justify-content: space-between; align-items: center;"><span>🗺️ Basic Walkthrough</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

<h3 id="first-use">First Use/Trial</h3>

When using Operit AI for the first time, you'll need to perform a simple setup to grant the necessary permissions to unlock its full functionality. Here are the detailed steps:
>Demonstration version `1.1.5`. Later versions will have a 'Skip' button in the top right corner.

<table style="width: 100%;">
  <thead>
    <tr>
      <th style="width: 25%; text-align: left;">Step</th>
      <th style="width: 45%; text-align: left;">Explanation & Action</th>
      <th style="width: 30%; text-align: left;">Screenshot</th>
    </tr>
  </thead>
  <tbody>
    <tr id="step-1">
      <td style="vertical-align: top;"><strong>Step 1: Read Our Agreement</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">
        <em>We declare that data flows only between your local device and the API platform you provide. We do not have any servers.</em>
      </td>
      <td style="vertical-align: top;">
        <a href="assets/user_step/step_for_frist_1.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/user_step/step_for_frist_1.jpg" alt="User Agreement and Privacy Policy" height="280">
        </a>
      </td>
    </tr>
    <tr id="step-2">
      <td style="vertical-align: top;"><strong>Step 2: Find and Enable Operit AI in System Settings</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">
        You can go directly to the relevant settings page, or you may need to find Operit AI in the "Installed apps" list and tap to enter.<br>
        <em>Find "Operit AI" in the settings list and tap it to proceed with the configuration.</em>
        <blockquote>Versions after <code>1.1.6</code> can skip the guide.</blockquote>
        <blockquote>If you are stuck here and cannot grant certain permissions, please exit the app and turn off the screen before retrying to skip the guide, as this guide only appears on the first launch.</blockquote>
      </td>
      <td style="vertical-align: top;">
        <a href="assets/user_step/step_for_frist_2.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/user_step/step_for_frist_2.jpg" alt="Permission Guide" height="280">
        </a>
      </td>
    </tr>
    <tr id="step-3">
      <td style="vertical-align: top;"><strong>Step 3: Set User Preferences</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">
        On your first time, we will recommend you to go to the settings. This will determine how the AI perceives you.
        <blockquote>You can change this later in <code>Settings > User Preferences</code>. Customization is supported.</blockquote>
        <em>Turn on the "Use Operit AI" switch and tap "Allow" in the system pop-up window. This is a security warning; Operit AI will use this permission responsibly.</em>
      </td>
      <td style="vertical-align: top;">
        <a href="assets/user_step/step_for_frist_3.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/user_step/step_for_frist_3.jpg" alt="Preference Configuration" height="280">
        </a>
      </td>
    </tr>
    <tr id="step-4">
      <td style="vertical-align: top;"><strong>Step 4: Configure Your API</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">
        After configuration, you can return to Operit AI and start your journey with your smart assistant! Of course, you can also use the author's default API to get a full experience once (per day).<br>
        <em>Configure the API and start using it.</em>
        <blockquote>The AI's API and model can be changed in <code>Settings > AI Model Configuration > Model & Parameters / Functional Model Configuration</code>.</blockquote>
        <blockquote>The model prompt can be changed in <code>Settings > Personalization > Model Prompt Settings</code>, where some model parameter settings are also located.</blockquote>
      </td>
      <td style="vertical-align: top;">
        <a href="assets/user_step/step_for_frist_4.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/user_step/step_for_frist_4.jpg" alt="Start using after configuring API" height="280">
        </a>
      </td>
    </tr>
  </tbody>
</table>

<h3 id="package-web-app">How to Package a Web App Created by AI</h3>
<em>The following steps demonstrate how to package a web application developed by the AI. (Click images to enlarge)</em>
<br>
<table style="width: 100%;">
  <thead>
    <tr>
      <th style="text-align: center; padding: 8px;">Step 1: Go to the Packaging Page</th>
      <th style="text-align: center; padding: 8px;">Step 2: Start Packaging</th>
      <th style="text-align: center; padding: 8px;">Step 3: Set Application Information</th>
      <th style="text-align: center; padding: 8px;">Step 4: Download or Share</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/teach_step/1-1.png" target="_blank" rel="noopener noreferrer">
          <img src="assets/teach_step/1-1.png" alt="Go to Packaging" style="max-height: 280px; max-width: 100%; height: auto;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/teach_step/1-2.png" target="_blank" rel="noopener noreferrer">
          <img src="assets/teach_step/1-2.png" alt="Start Packaging" style="max-height: 280px; max-width: 100%; height: auto;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/teach_step/1-3.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/teach_step/1-3.jpg" alt="Set Information" style="max-height: 280px; max-width: 100%; height: auto;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/teach_step/1-4.jpg" target="_blank" rel="noopener noreferrer">
         <img src="assets/teach_step/1-4.jpg" alt="Download and Share" style="max-height: 280px; max-width: 100%; height: auto;">
        </a>
      </td>
    </tr>
  </tbody>
</table>
<br>

<h3 id="api-configuration">How to Configure Your Own API/Other Models</h3>

<h4 id="deepseek-api">Configure Your Own DeepSeek API</h4>
<em>Follow these steps to easily configure the DeepSeek API for use in Operit AI.</em>
<p>If you want to configure your own API (instead of using the default one provided in the app), you can follow this process:</p>
<h5>Step 1: Log in/Register on the DeepSeek Open Platform</h5>
<p>
  First, you need to visit the DeepSeek Open Platform and log in to your account. We have embedded the DeepSeek open platform within the software. If this is your first time, you will need to register.
</p>
<div style="display: flex; justify-content: space-around; gap: 10px; flex-wrap: nowrap;">
    <a href="assets/deepseek_API_step/1.png" target="_blank" rel="noopener noreferrer"><img src="assets/deepseek_API_step/1.png" alt="DeepSeek Website" style="width: 100%; height: auto; border: 1px solid #ddd; border-radius: 4px;"></a>
    <a href="assets/deepseek_API_step/2.png" target="_blank" rel="noopener noreferrer"><img src="assets/deepseek_API_step/2.png" alt="Login Page" style="width: 100%; height: auto; border: 1px solid #ddd; border-radius: 4px;"></a>
    <a href="assets/deepseek_API_step/3.png" target="_blank" rel="noopener noreferrer"><img src="assets/deepseek_API_step/3.png" alt="Console" style="width: 100%; height: auto; border: 1px solid #ddd; border-radius: 4px;"></a>
</div>

<table style="width: 100%;">
  <tbody>
    <tr>
      <td style="vertical-align: top; padding: 8px; width: 33%;">
        <h5>Step 2: Add Credits</h5>
        <p>API calls consume account credits. You can follow the guide in Figure 5 to top up. Even a small amount (e.g., 1 CNY) is enough for a long experience with the V3 model.</p>
      </td>
      <td style="vertical-align: top; padding: 8px; width: 33%;">
        <h5>Step 3: Create and Copy the API Key</h5>
        <p>After a successful top-up, click the "Create API" button on the left. <strong>Please note: The key is only displayed in full once upon creation. Be sure to copy and store it securely immediately.</strong></p>
      </td>
      <td style="vertical-align: top; padding: 8px; width: 33%;">
        <h5>Step 4: Configure the Key in Operit AI</h5>
        <p>After creating and copying the key, return to the Operit AI application. You can directly enter your API key on the configuration page.</p>
      </td>
    </tr>
    <tr>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/deepseek_API_step/4.png" target="_blank" rel="noopener noreferrer"><img src="assets/deepseek_API_step/4.png" alt="API Key Page" style="max-height: 200px; height: auto; border: 1px solid #ddd; border-radius: 4px;"></a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/deepseek_API_step/5.png" target="_blank" rel="noopener noreferrer"><img src="assets/deepseek_API_step/5.png" alt="Create Key" style="max-height: 200px; height: auto; border: 1px solid #ddd; border-radius: 4px;"></a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/deepseek_API_step/9.png" target="_blank" rel="noopener noreferrer"><img src="assets/deepseek_API_step/9.png" alt="Configure in App" style="max-height: 200px; height: auto; border: 1px solid #ddd; border-radius: 4px;"></a>
      </td>
    </tr>
  </tbody>
</table>

<p>We support most models, including Gemini. If there are newer models we don't support yet, please let us know!</p>

<h4 id="switch-models">Switch to Other AI Models</h4>
<p>You can switch and configure your desired AI model by following these steps:</p>
<table style="width: 100%;">
  <thead>
    <tr>
      <th style="text-align: center; padding: 8px;">Step 1: Go to Settings</th>
      <th style="text-align: center; padding: 8px;">Step 2: Select AI Model Configuration</th>
      <th style="text-align: center; padding: 8px;">Step 3: Model & Parameter Configuration</th>
      <th style="text-align: center; padding: 8px;">Step 4: Choose Your Model</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/model/1.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/model/1.jpg" alt="Step 1" style="height: 280px; width: auto; max-width: 100%;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/model/2.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/model/2.jpg" alt="Step 2" style="height: 280px; width: auto; max-width: 100%;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/model/3.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/model/3.jpg" alt="Step 3" style="height: 280px; width: auto; max-width: 100%;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
         <a href="assets/model/4.jpg" target="_blank" rel="noopener noreferrer">
           <img src="assets/model/4.jpg" alt="Step 4" style="height: 280px; width: auto; max-width: 100%;">
         </a>
      </td>
    </tr>
  </tbody>
</table>

<h3 id="shizuku-authorization">Shizuku Authorization Flow</h3>
<p>After completing the Shizuku configuration, all built-in packages (except <code>coderunner</code>) can be used.</p>

<h3 id="package-management">Package Management and MCP Usage</h3>
<p>Built-in packages (except <code>coderunner</code>) work out of the box. Other extension packages and MCPs depend on the Termux environment. Please ensure Termux is running in the background before use.</p>

<h4 id="enabling-packages">Enabling Packages</h4>
<table style="width: 100%;">
    <thead>
      <tr>
       <th style="text-align: center; padding: 8px;">Step 1: Go to Package Management</th>
        <th style="text-align: center; padding: 8px;">Step 2: Enable Required Extension Packages</th>
      </tr>
     <p>Built-in packages (except <code>coderunner</code>) work out of the box. Other extension packages and MCPs depend on the Termux environment. Please ensure Termux is running in the background before use.</p>
    </thead>
    <tbody>
      <tr>
       <td style="text-align: center; padding: 8px; vertical-align: top;">
          <a href="assets/package_or_MCP/1.jpg" target="_blank" rel="noopener noreferrer"><img src="assets/package_or_MCP/1.jpg" alt="Enable Packages 1" style="height: 280px; width: auto; max-width: 100%;"></a>
       </td>
       <td style="text-align: center; padding: 8px; vertical-align: top;">
          <a href="assets/package_or_MCP/2.jpg" target="_blank" rel="noopener noreferrer"><img src="assets/package_or_MCP/2.jpg" alt="Enable Packages 2" style="height: 280px; width: auto; max-width: 100%;"></a>
       </td>
     </tr>
   </tbody>
</table>

<h4 id="environment-configuration">One-Click Environment Configuration</h4>
<table style="width: 100%;">
  <thead>
    <tr>
      <th style="width: 25%; text-align: left;">Step</th>
      <th style="width: 25%; text-align: left;">Explanation & Action</th>
      <th style="width: 50%; text-align: left;">Screenshot</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="vertical-align: top;"><strong>Step 1: Go to Toolbox</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">Find and tap the "Toolbox" entry on the main screen or in settings.</td>
      <td style="vertical-align: top;">
        <a href="assets/package_or_MCP/3.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/package_or_MCP/3.jpg" alt="Environment Config 1" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><strong>Step 2: Select Auto-configure Terminal</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">In the Toolbox, find and select the "Auto-configure Terminal" feature to begin the automated setup.</td>
      <td style="vertical-align: top;">
        <a href="assets/package_or_MCP/4.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/package_or_MCP/4.jpg" alt="Environment Config 2" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><strong>Step 3: Start Configuration</strong></td>
      <td style="vertical-align: top; padding-right: 15px;">Click the "Start Configuration" button, and the system will automatically install and configure the required environment.</td>
      <td style="vertical-align: top;">
        <a href="assets/package_or_MCP/5.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/package_or_MCP/5.jpg" alt="Environment Config 3" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
  </tbody>
</table>

<h4 id="mcp-configuration">MCP Configuration Flow</h4>
<table style="width: 100%;">
  <thead>
    <tr>
      <th style="text-align: center; padding: 8px;">Step 1: Go to MCP Market</th>
      <th style="text-align: center; padding: 8px;">Step 2: Click the Refresh Button</th>
      <th style="text-align: center; padding: 8px;">Step 3: Wait for Loading to Complete</th>
      <th style="text-align: center; padding: 8px;">Step 4: Select and Use an MCP</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/package_or_MCP/7.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/package_or_MCP/7.jpg" alt="MCP Config 1" style="height: 280px; width: auto; max-width: 100%;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/package_or_MCP/8.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/package_or_MCP/8.jpg" alt="MCP Config 2" style="height: 280px; width: auto; max-width: 100%;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
        <a href="assets/package_or_MCP/9.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/package_or_MCP/9.jpg" alt="MCP Config 3" style="height: 280px; width: auto; max-width: 100%;">
        </a>
      </td>
      <td style="text-align: center; padding: 8px; vertical-align: top;">
         <a href="assets/package_or_MCP/10.jpg" target="_blank" rel="noopener noreferrer">
           <img src="assets/package_or_MCP/10.jpg" alt="MCP Config 4" style="height: 280px; width: auto; max-width: 100%;">
         </a>
      </td>
    </tr>
  </tbody>
</table>

<div STYLE="page-break-after: always;"></div>

<h2 id="advanced-usage-in-practice" style="display: flex; justify-content: space-between; align-items: center;"><span>🚀 Advanced Usage in Practice</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

*(This section will show you how to use advanced features like extension packages and plan mode to complete more complex tasks through practical examples.)*

<h3 id="out-of-the-box">🧰 Out-of-the-Box</h3>
<em>This part covers the <strong>built-in packages</strong>.</em>
<br>
When you ask the AI to write software, the performance of the software depends on the AI's capabilities. The model in the example is <code>Deepseel-R1</code>.

<table style="width: 100%;">
  <thead>
    <tr>
      <th style="width: 20%; text-align: left;">Example</th>
      <th style="width: 30%; text-align: left;">Description</th>
      <th style="width: 50%; text-align: left;">Preview</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="vertical-align: top;"><strong>Create a 2D bullet hell game</strong></td>
      <td style="vertical-align: top;">
        Through simple conversation, let the AI design and implement a classic 2D bullet hell shooter game for you. Operit AI can use its basic coding abilities to build the complete game logic and dynamic graphics from scratch using only HTML and JavaScript.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/game_maker_chat.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/game_maker_chat.jpg" alt="2D bullet hell game chat" style="width: 100%; height: auto; margin-bottom: 5px;">
        </a>
        <a href="assets/game_maker_show.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/game_maker_show.jpg" alt="2D bullet hell game show" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><strong>Create a 3D game with HTML</strong></td>
      <td style="vertical-align: top;">
        Without any extension packages, Operit AI can create a dynamic 3D game scene for you using only its built-in core tools with HTML and JavaScript code.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/3ddebdde4958ac152eeca436e39c0f6.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/3ddebdde4958ac152eeca436e39c0f6.jpg" alt="3D Game Example 1" style="width: 100%; height: auto; margin-bottom: 5px;">
        </a>
        <a href="assets/expamle/759d86a7d74351675b32acb6464585d.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/759d86a7d74351675b32acb6464585d.jpg" alt="3D Game Example 2" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><strong>Simple video processing</strong></td>
      <td style="vertical-align: top;">
        Similarly, the application has a powerful FFmpeg tool built-in. No additional installation is required to let the AI help you with various video processing tasks such as format conversion, trimming, and merging.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/d7580a42ae03c723121bd172e1f9e7d.jpg" target="_blank" rel="noopener noreferrer">
          <img src="./assets/d7580a42ae03c723121bd172e1f9e7d.jpg" alt="Simple video processing example" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><strong>Software packaging and deployment</strong></td>
      <td style="vertical-align: top;">
        From writing code to final release, Operit AI can further call upon platform tools to package the completed software into executable files for Android (APK) or Windows (EXE), achieving an end-to-end automated development process.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/web_developer.jpg" target="_blank" rel="noopener noreferrer">
          <img src="./assets/web_developer.jpg" alt="Software packaging example 1" style="width: 100%; height: auto; margin-bottom: 5px;">
        </a>
        <a href="assets/game_maker_packer.jpg" target="_blank" rel="noopener noreferrer">
          <img src="./assets/game_maker_packer.jpg" alt="Software packaging example 2" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
  </tbody>
</table>

<h3 id="extension-packages">📦 Extension Packages</h3>
>Demonstration version `1.1.6`
(Click images to enlarge)

<table style="width: 100%;">
  <thead>
    <tr>
      <th style="width: 20%; text-align: left;">Package</th>
      <th style="width: 30%; text-align: left;">Description</th>
      <th style="width: 50%; text-align: left;">Preview</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="vertical-align: top;"><code>writer</code></td>
      <td style="vertical-align: top;">
        Advanced file editing and reading functions, supporting segmented editing, diff editing, line number editing, and advanced file reading operations.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/065e5ca8a8036c51a7905d206bbb56c.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/065e5ca8a8036c51a7905d206bbb56c.jpg" alt="writer example" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>various_search</code></td>
      <td style="vertical-align: top;">
        Multi-platform search function, supporting search results from platforms like Bing, Baidu, Sogou, Quark, etc.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/90a1778510df485d788b80d4bc349f9.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/90a1778510df485d788b80d4bc349f9.jpg" alt="Multi-platform search example 1" style="width: 100%; height: auto; margin-bottom: 5px;">
        </a>
        <a href="assets/expamle/f9b8aeba4878775d1252ad8d5d8620a.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/f9b8aeba4878775d1252ad8d5d8620a.jpg" alt="Multi-platform search example 2" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>daily_life</code></td>
      <td style="vertical-align: top;">
        A collection of daily life tools, including date and time query, device status monitoring, weather search, alarm setting, SMS and phone communication, etc.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/615cf7a99e421356b6d22bb0b9cc87b.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/615cf7a99e421356b6d22bb0b9cc87b.jpg" alt="Daily life example" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>super_admin</code></td>
      <td style="vertical-align: top;">
        Super administrator toolset, providing advanced functions for terminal commands and Shell operations.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/731f67e3d7494886c1c1f8639216bf2.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/731f67e3d7494886c1c1f8639216bf2.jpg" alt="Super admin example 1" style="width: 100%; height: auto; margin-bottom: 5px;">
        </a>
        <a href="assets/expamle/6f81901ae47f5a3584167148017d132.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/6f81901ae47f5a3584167148017d132.jpg" alt="Super admin example 2" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>code_runner</code></td>
      <td style="vertical-align: top;" colspan="2">Multi-language code execution capability, supporting the execution of JavaScript, Python, Ruby, Go, and Rust scripts.<br><em>You can configure these environments in <code>Toolbox > Auto-configure Terminal</code>.</em></td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>baidu_map</code></td>
      <td style="vertical-align: top;">
        Baidu Maps related functions.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/71fd917c5310c1cebaa1abb19882a6d.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/71fd917c5310c1cebaa1abb19882a6d.jpg" alt="Baidu Maps example" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>qq_intelligent</code></td>
      <td style="vertical-align: top;" colspan="2">QQ intelligent assistant, enabling interaction with the QQ app through UI automation.</td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>time</code></td>
      <td style="vertical-align: top;" colspan="2">Provides time-related functions.</td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>various_output</code></td>
      <td style="vertical-align: top;">
        Provides image output functionality.
      </td>
      <td style="vertical-align: top; text-align: center;">
        <a href="assets/expamle/5fff4b49db78ec01e189658de8ea997.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/expamle/5fff4b49db78ec01e189658de8ea997.jpg" alt="Image output example" style="width: 100%; height: auto;">
        </a>
      </td>
    </tr>
  </tbody>
</table>


<h3 id="core-tools">🛠️ Core Tools</h3>

| Tool | Description |
|---|---|
| `sleep` | Pauses execution for a short period. |
| `device_info` | Gets detailed device information. |
| `use_package` | Activates an extension package. |
| `query_problem_library` | Queries the problem library. |
| `list_files` | Lists files in a directory. |
| `read_file` | Reads the content of a file. |
| `write_file` | Writes content to a file. |
| `delete_file` | Deletes a file or directory. |
| `file_exists` | Checks if a file exists. |
| `move_file` | Moves or renames a file. |
| `copy_file` | Copies a file or directory. |
| `make_directory` | Creates a directory. |
| `find_files` | Finds matching files. |
| `zip_files/unzip_files` | Compresses/decompresses files. |
| `download_file` | Downloads a file from the network. |
| `http_request` | Sends an HTTP request. |
| `multipart_request` | Uploads a file. |
| `manage_cookies` | Manages cookies. |
| `visit_web` | Visits and extracts content from a web page. |
| `get_system_setting` | Gets a system setting. |
| `modify_system_setting` | Modifies a system setting. |
| `install_app/uninstall_app`| Installs/uninstalls an app. |
| `start_app/stop_app` | Starts/stops an app. |
| `get_notifications` | Gets device notifications. |
| `get_device_location` | Gets the device location. |
| `get_page_info` | Gets UI screen information. |
| `tap` | Simulates a tap at coordinates. |
| `click_element` | Clicks a UI element. |
| `set_input_text` | Sets input text. |
| `press_key` | Simulates a key press. |
| `swipe` | Simulates a swipe gesture. |
| `find_element` | Finds a UI element. |
| `ffmpeg_execute` | Executes an FFmpeg command. |
| `ffmpeg_info` | Gets FFmpeg information. |
| `ffmpeg_convert` | Converts a video file. |

<h3 id="mcp-market">🛒 MCP Market</h3>

> Considering the specifics of the mobile environment, it is quite challenging to fully and stably configure the environments required for all MCPs (Model context protocol). Therefore, calling MCPs directly may encounter many difficulties.
>
> Currently, the MCPs confirmed to be available in the app are mainly `12306` and `fetch`. However, it should be noted that the search effectiveness and stability of `fetch` are not as good as our deeply optimized `various_search` extension package.
>
> To provide a smoother and more reliable experience, we have remade and integrated the functionality of many core MCPs into the built-in tools and extension packages in a way that is more suitable for the Android system. We strongly recommend that you prioritize using these optimized features.

> Below are some MCPs currently tested and available by the community:

(Click images to enlarge)

<table style="width: 100%;">
  <thead>
    <tr>
      <th style="width: 25%; text-align: left;">MCP (Package)</th>
      <th style="width: 50%; text-align: left;">Description</th>
      <th style="width: 25%; text-align: left;">Preview</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td style="vertical-align: top;"><code>tavily</code></td>
      <td style="vertical-align: top;">
        Tavily search tool, providing powerful web search capabilities for research and information gathering.
      </td>
      <td style="vertical-align: top; text-align: right;">
        <a href="assets/ee852df3c187771fba0aa92b36a57f8.jpg" target="_blank" rel="noopener noreferrer">
          <img src="assets/ee852df3c187771fba0aa92b36a57f8.jpg" alt="Tavily search example" height="280">
        </a>
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>12306</code></td>
      <td style="vertical-align: top;" colspan="2">
        Used to query 12306 train ticket information.
      </td>
    </tr>
    <tr>
      <td style="vertical-align: top;"><code>fetch</code></td>
      <td style="vertical-align: top;" colspan="2">
        A general-purpose tool for fetching web content. <em>Note: Its search effectiveness and stability are inferior to the deeply optimized <code>various_search</code> extension package.</em>
      </td>
    </tr>
  </tbody>
</table>

### ⏳ Plan Mode
No longer exists in version `1.1.6` and later.

Suitable for long-running AI tasks, but the difference from not using plan mode is not significant (and the effect might even be better without it). It will be removed in a future version and replaced with `Task Mode`.

>`Task Mode`, the AI may proactively send you messages.

>Note: Improper use will accelerate token consumption.

<div STYLE="page-break-after: always;"></div>

<h2 id="faq" style="display: flex; justify-content: space-between; align-items: center;"><span>❔ Frequently Asked Questions (FAQ)</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

This section contains all the questions from the user group and issues for the **latest version `1.1.6`**.
If you are using an older version, you can look for answers <a href="#reminiscence">here</a>.

<h3 id="mcp-troubleshooting">MCP Troubleshooting</h3>

**Possible Reasons for MCP Packages Not Loading**
- **Shizuku not configured correctly**: Please refer to the <a href="#shizuku-authorization">Shizuku Authorization Flow</a> to complete the configuration.
- **Termux not configured correctly**: Please refer to the <a href="#package-management">Package Management and MCP Usage</a> to complete the configuration.
- **Termux not running in the background**: Before starting the app, be sure to open Termux and keep it running in the background.

**Reasons for MCP Execution Failure**
- **Environment configuration issues**: Some MCP plugins have special requirements for the operating environment. You need to visit the respective plugin's GitHub repository and configure the environment according to its documentation.
- **Version compatibility issues**: Most problems from earlier versions have been resolved in subsequent updates. We strongly recommend downloading and installing the latest version for the best experience.

You can download the latest APK from the [Release page](https://github.com/AAswordman/Operit/releases).

<div STYLE="page-break-after: always;"></div>
<h2 id="join-us" style="display: flex; justify-content: space-between; align-items: center;"><span>🎉 Join Us</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

We sincerely invite you to join our community to exchange ideas with other users, share your creativity, or give us valuable suggestions.

Currently, we recommend interacting with the creator and other users in the <a href="https://github.com/AAswordman/Operit/discussions" target="_blank" rel="noopener noreferrer"><strong>GitHub Discussions</strong></a> section, as there is no single community group suitable for all regions. If you are a Chinese-speaking user, you are welcome to join our QQ group mentioned in the Chinese guide.

<div STYLE="page-break-after: always;"></div>

<h2 id="wishlist" style="display: flex; justify-content: space-between; align-items: center;"><span>💡 Wishlist</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

Have a great idea or feature suggestion? We encourage you to share it in our <a href="https://github.com/AAswordman/Operit/discussions" target="_blank" rel="noopener noreferrer"><strong>GitHub Discussions</strong></a>! You can also check out our future update plans—the feature you're hoping for might already be on its way!

### 🚀 Future Update Plan

Here are the features we are planning or currently developing:

- **Core Feature Enhancements**:
  - Add TTS (Text-to-Speech) and voice recognition models to further realize a more natural dialogue system.
  - Implement a brand new `Task Mode` to replace the current `Plan Mode`, allowing the AI to proactively and intelligently execute and follow up on long-term tasks.
- **User Experience Optimization**:
  - Implement a more beautiful, modern, and friendly user interface.
  - Support for multiple languages to allow global users to use it without barriers.
- **Community and Ecosystem**:
  - We will take every suggestion from the community (like Issues and <a href="https://github.com/AAswordman/Operit/discussions" target="_blank" rel="noopener noreferrer">GitHub Discussions</a>) seriously and strive to make them a reality.
  - Promote! Promote! Promote! Let more people know about and use Operit AI.


<div STYLE="page-break-after: always;"></div>

<h2 id="reminiscence" style="display: flex; justify-content: space-between; align-items: center;"><span>📜 Reminiscence (Older Version FAQ)</span><a href="#table-of-contents" style="text-decoration: none; font-size: 0.8em;" title="Back to Top">🔝</a></h2>

#### Version `1.1.5`
*(Related issues)*


#### Before `1.1.3`
*(Related issues)*
##### Gemini format not adapted
This has been resolved in the new version, with support for more models.
##### Termux
###### Type 1: Incorrect Termux version
###### Type 2: MCP packages not loading
It is recommended to keep Termux running in the background when the app is running.

Future versions will address these issues by embedding Termux. 