#!/bin/bash

# Documentation Generation Script for FueledByChai Trading

echo "🚀 Starting FueledByChai Trading Documentation Generation..."

# Set working directory

# Create output directory
mkdir -p docs/generated
echo "📁 Created output directory: docs/generated"

# Compile the project
echo "🔨 Compiling project..."
mvn clean compile -q

# Check if compilation was successful
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✅ Compilation successful"

# Generate documentation using Maven exec plugin
echo "📝 Generating documentation..."
mvn exec:java -pl examples/CryptoExamples \
    -Dexec.mainClass="com.fueledbychai.documentation.GenerateBrokerDocumentation" \
    -Dexec.args="docs/generated" \
    -q

# Check results
if [ -f "docs/generated/README.md" ]; then
    echo "✅ Documentation generation completed successfully!"
    echo ""
    echo "📖 Generated files:"
    ls -la docs/generated/
    echo ""
    echo "🔍 Quick preview of README.md:"
    head -20 docs/generated/README.md
else
    echo "❌ Documentation generation failed - README.md not found"
    echo "🔍 Checking docs/generated directory:"
    ls -la docs/generated/ 2>/dev/null || echo "Directory doesn't exist"
fi