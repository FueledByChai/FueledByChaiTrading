# FueledByChai Trading - Development Workflow & Best Practices

## Overview

This document outlines the development workflow and best practices for the FueledByChai Trading library. The approach is designed to scale from solo development to team collaboration while maintaining code quality and release integrity.

## Current State (Sole Developer) - Recommended Workflow

### Branch Strategy: GitHub Flow (Simplified)

For heavy development as a sole developer, we use a **lightweight but structured approach**:

```
main (stable, always deployable)
├── feature/paradex-order-management
├── feature/hyperliquid-websocket-client
├── feature/broker-api-enhancements
└── hotfix/connection-retry-logic
```

**Branch Types:**

- `main` - Always stable, ready for release
- `feature/descriptive-name` - New functionality
- `hotfix/issue-description` - Critical bug fixes
- `release/v0.3.0` - Release preparation (optional)

### Development Workflow Options

#### Option A: Feature Branches with Self-Review (Recommended)

```bash
# Start new feature
git checkout main
git pull origin main
git checkout -b feature/paradex-order-cancellation

# Develop and commit regularly
git add .
git commit -m "feat: add order cancellation endpoint to ParadexRestApi"
git commit -m "test: add unit tests for order cancellation"
git commit -m "docs: update API documentation for cancellation"

# Push and create PR for self-review and documentation
git push -u origin feature/paradex-order-cancellation
# Create PR on GitHub (even for self-review)

# After testing, merge to main
git checkout main
git merge feature/paradex-order-cancellation
git push origin main
git branch -d feature/paradex-order-cancellation
```

#### Option B: Direct to Main (Faster, but less structured)

```bash
# For small, well-tested changes
git checkout main
git add .
git commit -m "feat: improve HyperliquidClient error handling"
git push origin main
```

## Commit Message Standards

Use **Conventional Commits** for better tracking:

```bash
feat: add new ParadexBroker order placement functionality
fix: resolve WebSocket reconnection issue in HyperliquidClient
docs: update README with new broker setup instructions
test: add integration tests for order lifecycle
refactor: simplify error handling in REST clients
perf: optimize quote processing in market data handlers
chore: update dependencies and build configuration
```

### Commit Types:

- `feat:` - New features
- `fix:` - Bug fixes
- `docs:` - Documentation changes
- `test:` - Adding or updating tests
- `refactor:` - Code refactoring
- `perf:` - Performance improvements
- `chore:` - Maintenance tasks

## When to Use Pull Requests (Even as Solo Dev)

### Always use PRs for:

- Major features (new broker implementations)
- Breaking changes
- Complex refactoring
- When you want to document the change for future reference

### Direct commits OK for:

- Bug fixes
- Small improvements
- Documentation updates
- Test additions

## Daily Workflow

### Start of Day

```bash
# Sync with latest
git checkout main
git pull origin main
```

### Development Cycle

```bash
# Create feature branch for significant work
git checkout -b feature/hyperliquid-order-routing

# Regular development cycle
# ... make changes ...
git add .
git commit -m "feat: implement order routing logic"

# Push regularly (backup & sharing progress)
git push -u origin feature/hyperliquid-order-routing

# When feature complete, merge to main
git checkout main
git merge feature/hyperliquid-order-routing
git push origin main
```

## Testing Strategy

### Before Each Commit

```bash
mvn clean test
```

### Before Merging to Main

```bash
mvn clean verify
```

### Before Releases

```bash
mvn clean install -Prelease
```

## Versioning Strategy

### Semantic Versioning (SemVer)

- **Major version** (X.0.0): Breaking API changes
  - Example: Removing public methods, changing method signatures
- **Minor version** (0.Y.0): New features, backward compatible
  - Example: Adding new brokers, new API methods
- **Patch version** (0.0.Z): Bug fixes, backward compatible
  - Example: Fixing calculation errors, connection issues

### Version Format

`MAJOR.MINOR.PATCH[-SNAPSHOT]`

- 0.2.0-SNAPSHOT (development)
- 0.2.0 (release)
- 0.2.1-SNAPSHOT (next development)

### Release Commands

```bash
# Prepare release (removes SNAPSHOT, creates tag, bumps to next SNAPSHOT)
mvn release:prepare

# Perform release (builds and deploys from tag)
mvn release:perform

# Manual version updates if needed
mvn versions:set -DnewVersion=1.0.0-SNAPSHOT
mvn versions:commit
```

## Release Management

### Release Cadence

- **Patch releases (0.2.1)** - As needed for bugs
- **Minor releases (0.3.0)** - Monthly or when major features complete
- **Major releases (1.0.0)** - When API stability achieved

### Quality Gates

```bash
# Before any release
mvn clean verify
mvn dependency:analyze
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates
```

### Release Process

1. Update version in `CHANGELOG.md`
2. Run `mvn release:prepare`
3. Run `mvn release:perform`
4. Create GitHub release with changelog
5. Update documentation if needed

## Documentation Updates

### Required Updates

- Update `CHANGELOG.md` with each significant change
- Keep README current with new features
- Document API changes in code
- Update JavaDoc for public APIs

## Future Multi-Developer Workflow

When contributors join the project, evolve to:

### Protected Main Branch

- Require PR reviews
- Require status checks (CI)
- No direct pushes to main

### Enhanced PR Process

- All changes via pull requests
- Code review requirements
- Automated testing validation
- Documentation review

## Automated CI/CD

### GitHub Actions

- Automated testing on all PRs
- Build verification
- Dependency vulnerability scanning
- Code coverage reporting

### Quality Checks

- Unit test execution
- Integration test validation
- Code style enforcement
- Security scanning

## Project Structure

### Core Components

- **fueledbychai-api** - Core trading APIs
- **implementations** - Broker-specific implementations
- **commons** - Shared utilities and common code
- **examples** - Usage examples and demos

### Broker Implementations

- Interactive Brokers
- Paradex
- Hyperliquid
- Paper Broker (testing)

## Development Environment

### Requirements

- Java 21
- Maven 3.6+
- Git
- IDE with Maven support

### Setup

```bash
git clone https://github.com/FueledByChai/FueledByChaiTrading.git
cd FueledByChaiTrading
mvn clean install
```

## Contributing Guidelines

### Code Style

- Follow existing code patterns
- Use meaningful variable and method names
- Add appropriate comments and JavaDoc
- Maintain test coverage

### Testing Requirements

- Unit tests for all new functionality
- Integration tests for broker implementations
- Manual testing for complex workflows

## Issue Management

### Issue Types

- **Bug Report** - Problems with existing functionality
- **Feature Request** - New functionality proposals
- **Enhancement** - Improvements to existing features
- **Documentation** - Documentation updates or clarifications

### Labels

- `bug` - Bug reports
- `enhancement` - Feature requests
- `documentation` - Documentation updates
- `broker:ib` - Interactive Brokers specific
- `broker:paradex` - Paradex specific
- `broker:hyperliquid` - Hyperliquid specific

## Immediate Action Items

### For Current Development

1. ✅ Commit the workflow files created
2. ⏳ Set up branch protection on main (when ready for stricter workflow)
3. ⏳ Start using conventional commits
4. ⏳ Keep CHANGELOG.md updated
5. ⏳ Use feature branches for major work

### For Future Scaling

1. Enable branch protection rules
2. Set up required status checks
3. Configure automated dependency updates
4. Implement comprehensive test coverage reporting

---

This approach provides structure without slowing down development and offers a clear path to scale when adding contributors!
