# Contributing to Postgres Event Trigger Plugin

Thank you for your interest in contributing to the Postgres Event Trigger Plugin for Jenkins!

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 11 or later
- Apache Maven 3.8 or later
- Git
- A Supabase account (for testing)
- Jenkins 2.361.4 or later (for manual testing)

### Setting Up Development Environment

1. Fork the repository on GitHub

2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/jenkins-postgres-event-trigger-plugin.git
   cd jenkins-postgres-event-trigger-plugin
   ```

3. Build the project:
   ```bash
   mvn clean install
   ```

4. Run Jenkins with the plugin:
   ```bash
   mvn hpi:run
   ```
   This starts a Jenkins instance at http://localhost:8080/jenkins with the plugin loaded.

## Development Guidelines

### Code Style

- Follow standard Java coding conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public methods and classes
- Keep methods focused and concise
- Maximum line length: 120 characters

### Project Structure

```
jenkins-postgres-event-trigger-plugin/
├── src/
│   ├── main/
│   │   ├── java/io/jenkins/plugins/postgres/
│   │   │   ├── PostgresEventTrigger.java          # Main trigger implementation
│   │   │   ├── PostgresEventTriggerConfiguration.java  # Global configuration
│   │   │   ├── SupabaseInstance.java              # Instance configuration object
│   │   │   └── SupabaseRealtimeClient.java        # WebSocket client
│   │   └── resources/
│   │       ├── index.jelly                        # Plugin index
│   │       └── io/jenkins/plugins/postgres/
│   │           ├── PostgresEventTrigger/
│   │           │   ├── config.jelly              # Job-level UI
│   │           │   └── help-*.html               # Help files
│   │           ├── PostgresEventTriggerConfiguration/
│   │           │   └── config.jelly              # Global configuration UI
│   │           └── SupabaseInstance/
│   │               ├── config.jelly              # Instance configuration UI
│   │               └── help-*.html               # Help files
│   └── test/
│       └── java/io/jenkins/plugins/postgres/
│           ├── SupabaseInstanceTest.java
│           ├── PostgresEventTriggerConfigurationTest.java
│           └── PostgresEventTriggerTest.java
├── pom.xml                                        # Maven configuration
├── README.md                                      # User documentation
├── IMPLEMENTATION.md                              # Technical details
└── CONTRIBUTING.md                                # This file
```

### Making Changes

1. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Make your changes following the code style guidelines

3. Add or update tests for your changes

4. Run tests to ensure everything works:
   ```bash
   mvn test
   ```

5. Build the plugin:
   ```bash
   mvn clean package
   ```

6. Test manually in a running Jenkins instance:
   ```bash
   mvn hpi:run
   ```

### Testing

#### Unit Tests

- Write unit tests for all new functionality
- Use JenkinsRule for integration testing
- Mock external dependencies (WebSocket connections)
- Aim for high code coverage

Example test structure:
```java
public class YourFeatureTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testYourFeature() {
        // Arrange
        // Act
        // Assert
    }
}
```

#### Manual Testing

1. Start Jenkins with the plugin:
   ```bash
   mvn hpi:run
   ```

2. Configure a Supabase instance in Manage Jenkins → Configure System

3. Create a test job with the Postgres Event Trigger

4. Trigger events in your Supabase database

5. Verify builds are triggered correctly

### Committing Changes

#### Commit Messages

Follow conventional commit format:

```
type(scope): brief description

Detailed explanation if needed.

Fixes #issue_number
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `style`: Code style changes (formatting, etc.)
- `chore`: Maintenance tasks

Example:
```
feat(trigger): add support for event filtering

Add ability to filter events based on record values.
Users can now specify JSON path expressions to filter
which events trigger builds.

Fixes #123
```

#### Submitting Changes

1. Push your branch to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

2. Create a Pull Request on GitHub

3. Fill in the PR template with:
   - Description of changes
   - Related issues
   - Testing performed
   - Screenshots (for UI changes)

## Reporting Issues

### Bug Reports

When reporting a bug, include:

1. **Description**: Clear description of the issue
2. **Steps to Reproduce**: Detailed steps to reproduce the bug
3. **Expected Behavior**: What should happen
4. **Actual Behavior**: What actually happens
5. **Environment**:
   - Jenkins version
   - Plugin version
   - Java version
   - Operating system
6. **Logs**: Relevant log entries
7. **Screenshots**: If applicable

### Feature Requests

When requesting a feature, include:

1. **Use Case**: Why you need this feature
2. **Description**: What the feature should do
3. **Alternatives**: Other solutions you've considered
4. **Additional Context**: Any other relevant information

## Code Review Process

### What We Look For

- Code quality and readability
- Test coverage
- Documentation updates
- Backward compatibility
- Performance impact
- Security considerations

### Review Timeline

- Initial review: Within 1 week
- Follow-up reviews: As needed
- Merge: After approval and CI passes

## Communication

### Channels

- **GitHub Issues**: Bug reports and feature requests
- **Pull Requests**: Code contributions
- **Discussions**: General questions and ideas

### Response Time

- We aim to respond to issues within 1 week
- Pull requests reviewed within 2 weeks
- Critical bugs addressed urgently

## Release Process

1. Version bump in pom.xml
2. Update CHANGELOG.md
3. Tag release in Git
4. Build and test release
5. Publish to Jenkins Update Center

## Legal

### Contributor License Agreement

By contributing, you agree that your contributions will be licensed under the MIT License.

### Code of Conduct

We follow the Jenkins Code of Conduct. Be respectful and professional in all interactions.

## Resources

### Jenkins Plugin Development

- [Jenkins Plugin Tutorial](https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial)
- [Jenkins Plugin Development Guide](https://www.jenkins.io/doc/developer/plugin-development/)
- [Jenkins API Documentation](https://javadoc.jenkins.io/)

### Supabase

- [Supabase Realtime Documentation](https://supabase.com/docs/guides/realtime)
- [Supabase API Reference](https://supabase.com/docs/reference)

### Tools

- [Maven Documentation](https://maven.apache.org/guides/)
- [Java-WebSocket Library](https://github.com/TooTallNate/Java-WebSocket)
- [Gson Documentation](https://github.com/google/gson)

## Getting Help

If you need help:

1. Check the README.md and IMPLEMENTATION.md
2. Search existing issues on GitHub
3. Create a new issue with your question
4. Be specific and provide context

## Recognition

Contributors will be:
- Listed in the CONTRIBUTORS file
- Mentioned in release notes
- Acknowledged in the project documentation

Thank you for contributing to the Postgres Event Trigger Plugin!
