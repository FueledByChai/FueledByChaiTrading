#!/bin/bash

# Fix Git Author History Script
# This script rewrites commit history to use anonymous author

echo "🔧 Fixing Git commit author history..."

# Configuration
OLD_NAME="Rob Terpilowski"  # Your real name
OLD_EMAIL="your-personal-email@example.com"  # Your personal email
NEW_NAME="FueledByChai"
NEW_EMAIL="fueledbychai@users.noreply.github.com"

echo "📋 Will change commits from:"
echo "   $OLD_NAME <$OLD_EMAIL>"
echo "📋 To:"
echo "   $NEW_NAME <$NEW_EMAIL>"
echo ""

read -p "⚠️  This will rewrite git history. Continue? (y/N): " confirm
if [[ $confirm != [yY] ]]; then
    echo "❌ Aborted"
    exit 1
fi

echo ""
echo "🔄 Rewriting commit history..."

# Rewrite commit history
git filter-branch --env-filter "
if [ \"\$GIT_COMMITTER_NAME\" = \"$OLD_NAME\" ]
then
    export GIT_COMMITTER_NAME=\"$NEW_NAME\"
    export GIT_COMMITTER_EMAIL=\"$NEW_EMAIL\"
fi
if [ \"\$GIT_AUTHOR_NAME\" = \"$OLD_NAME\" ]
then
    export GIT_AUTHOR_NAME=\"$NEW_NAME\"
    export GIT_AUTHOR_EMAIL=\"$NEW_EMAIL\"
fi
" --tag-name-filter cat -- --branches --tags

echo ""
echo "✅ History rewritten!"
echo ""
echo "⚠️  Important: If you've already pushed to GitHub, you'll need to force push:"
echo "   git push --force-with-lease origin Branch-0.2.0"
echo ""
echo "🔍 Verify the changes:"
echo "   git log --oneline -5 --pretty=format:\"%h %an <%ae> %s\""