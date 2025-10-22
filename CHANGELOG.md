# FueledByChai Trading - Release Notes

## [Unreleased]

### Added

- New HyperliquidBroker implementation with WebSocket support
- ParadexBroker order management enhancements
- Improved error handling across all broker implementations

### Changed

- Updated REST API clients to use connection pooling
- Refactored market data processing for better performance

### Fixed

- WebSocket reconnection logic in crypto exchanges
- Order status synchronization issues

### Security

- Updated dependencies to address security vulnerabilities

## [0.2.0] - 2025-10-17

### Added

- Initial ParadexBroker implementation
- HyperliquidClient REST API integration
- Paper trading broker for testing
- Comprehensive unit test coverage

### Changed

- Migrated from SumZero Trading to FueledByChai Trading
- Updated to Java 21
- Modernized Maven configuration

### Fixed

- Interactive Brokers API compatibility issues
- Market data subscription stability

## [0.1.6] - Previous releases

- See git history for older releases

---

## Release Process

1. Update version in `CHANGELOG.md`
2. Run `mvn release:prepare`
3. Run `mvn release:perform`
4. Create GitHub release with changelog
5. Update documentation if needed
