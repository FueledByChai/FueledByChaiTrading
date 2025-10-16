#!/bin/bash

# Setup New Anonymous Repository Script
# This script helps you migrate your rebranded code to a new anonymous GitHub repo

echo "🚀 Setting up new anonymous repository..."

# Configuration - UPDATE THESE VALUES
NEW_REPO_URL="https://github.com/fueledbychai-org/FueledByChaiTrading.git"  # UPDATE THIS
NEW_REPO_NAME="FueledByChaiTrading"
ANONYMOUS_NAME="FueledByChai Contributors"
ANONYMOUS_EMAIL="contributors@fueledbychai.dev"  # UPDATE THIS

echo "📋 Configuration:"
echo "   New repo: $NEW_REPO_URL"
echo "   Author name: $ANONYMOUS_NAME"
echo "   Author email: $ANONYMOUS_EMAIL"
echo ""

# Confirm before proceeding
read -p "⚠️  This will change git configuration and remotes. Continue? (y/N): " confirm
if [[ $confirm != [yY] ]]; then
    echo "❌ Aborted"
    exit 1
fi

echo ""
echo "🔧 Step 1: Configure git with anonymous identity..."

# Set local git config for this repository
git config user.name "$ANONYMOUS_NAME"
git config user.email "$ANONYMOUS_EMAIL"

echo "✅ Git identity configured"

echo ""
echo "🔗 Step 2: Adding new remote..."

# Remove old origin (if you want to break the link to personal repo)
if git remote get-url origin > /dev/null 2>&1; then
    echo "📝 Renaming current origin to 'old-origin'..."
    git remote rename origin old-origin
fi

# Add new remote
git remote add origin "$NEW_REPO_URL"

echo "✅ New remote added"

echo ""
echo "🔍 Step 3: Checking repository status..."

# Show current status
echo "📊 Current git status:"
git status --short

# Show recent commits (to check author info)
echo ""
echo "📝 Recent commits:"
git log --oneline -5 --pretty=format:"%h %an <%ae> %s"

echo ""
echo ""
echo "🚀 Step 4: Ready to push to new repository!"
echo ""
echo "📋 Next manual steps:"
echo "1. Review the changes above"
echo "2. Create the repository '$NEW_REPO_NAME' on GitHub"
echo "3. Run: git push -u origin Branch-0.2.0"
echo "4. Set Branch-0.2.0 as default branch on GitHub"
echo "5. Create release and tags"
echo ""

# Optional: Create a new commit with anonymous author
read -p "🔄 Create a new 'Initial FueledByChai commit' with anonymous author? (y/N): " create_commit
if [[ $create_commit == [yY] ]]; then
    echo ""
    echo "📝 Creating anonymous initial commit..."
    
    # Stage all changes
    git add .
    
    # Create new commit with anonymous author
    git commit -m "Initial FueledByChai Trading Framework

- Rebranded from SumZero Trading
- Updated package structure to com.fueledbychai.*
- Removed personal branding
- Enhanced documentation and examples

Features:
- Multi-broker connectivity (Interactive Brokers, Crypto exchanges)
- Market data feeds and historical data access
- Strategy development framework
- Real-time order management" \
    --author="$ANONYMOUS_NAME <$ANONYMOUS_EMAIL>"
    
    echo "✅ Anonymous commit created"
fi

echo ""
echo "🎉 Setup complete!"
echo ""
echo "🔗 Repository remotes:"
git remote -v
echo ""
echo "👤 Current git config:"
echo "   Name: $(git config user.name)"
echo "   Email: $(git config user.email)"
echo ""
echo "⚡ Ready to push:"
echo "   git push -u origin Branch-0.2.0"