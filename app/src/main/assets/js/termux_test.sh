#!/bin/bash
# Simple Termux test script
echo "Termux API Test"
echo "Current date: $(date)"
echo "Termux version: $(pkg --version 2>/dev/null || echo 'not available')"
echo "System info: $(uname -a)"
echo "Storage access: $(ls -la /sdcard 2>/dev/null && echo 'OK' || echo 'Not available')"
echo "Test complete!" 