# Changelog

All notable changes to the Postgres Event Trigger Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial implementation of Postgres Event Trigger Plugin
- Support for monitoring Postgres/Supabase database tables via Realtime
- Global configuration for managing multiple Supabase instances
- Job-level trigger configuration for subscribing to table events
- Support for INSERT, UPDATE, and DELETE events
- Event data passed to builds as environment variables
- Secure credential management using Jenkins Credentials Plugin
- WebSocket-based real-time connection to Supabase
- Comprehensive documentation and examples
- Unit tests for core components

## [1.0.0] - TBD

### Added
- First stable release
- Core trigger functionality
- Supabase Realtime integration
- Multi-table and multi-instance support
- Build parameter injection with event data
- Help documentation for all configuration fields
- Example configurations for common use cases

### Features
- **Global Configuration**: Configure multiple Supabase instances
- **Job Triggers**: Subscribe to database events per job
- **Event Types**: Support for INSERT, UPDATE, DELETE
- **Multi-table**: Monitor multiple tables in a single job
- **Schema Support**: Specify schema for tables (e.g., public.users)
- **Event Data**: Full event payload available in builds
- **Secure**: API keys stored in Jenkins Credentials
- **Real-time**: WebSocket-based monitoring for instant triggers

### Requirements
- Jenkins 2.361.4 or later
- Java 11 or later
- Supabase project with Realtime enabled

---

## Version History Format

### [Version] - YYYY-MM-DD

#### Added
- New features

#### Changed
- Changes to existing functionality

#### Deprecated
- Soon-to-be removed features

#### Removed
- Removed features

#### Fixed
- Bug fixes

#### Security
- Security vulnerability fixes

---

[Unreleased]: https://github.com/radhakrisri/jenkins-postgres-event-trigger-plugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/radhakrisri/jenkins-postgres-event-trigger-plugin/releases/tag/v1.0.0
