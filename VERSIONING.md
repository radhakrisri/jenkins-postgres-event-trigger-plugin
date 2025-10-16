# Versioning Strategy

This project follows semantic versioning (SemVer) and uses git-changelist-maven-extension for automatic version management.

## Version Format

- **Release builds**: `X.Y.Z` (e.g., `1.1.0`)
- **Snapshot builds**: `X.Y.Z-SNAPSHOT` (e.g., `1.1.0-SNAPSHOT`)
- **Release candidates**: `X.Y.Z-RC.N` (e.g., `1.1.0-RC.1`)

## HPI File Naming

The generated HPI file follows Jenkins plugin naming conventions:
- **File name**: `jenkins-supabase.hpi`
- **ArtifactId**: `jenkins-supabase`
- **Display name**: "Jenkins Supabase Plugin"

## Release Process

1. **Development**: Work with SNAPSHOT versions (e.g., `1.1.0-SNAPSHOT`)
2. **Release**: Create a git tag with format `vX.Y.Z` (e.g., `v1.1.0`)
3. **Automatic versioning**: The git-changelist extension automatically:
   - Uses the tag version for release builds
   - Generates SNAPSHOT versions between releases

## Version Examples

```bash
# On untagged commit after v1.0.0
mvn help:evaluate -Dexpression=project.version -q -DforceStdout
# Output: 1.1.0-SNAPSHOT

# On tagged commit v1.1.0
git tag v1.1.0
mvn help:evaluate -Dexpression=project.version -q -DforceStdout  
# Output: 1.1.0

# Build artifacts
mvn clean package
# Generates: target/jenkins-supabase.hpi
```

## Manual Version Override

You can override the version during build:

```bash
# Build with specific version
mvn clean package -Dchangelist=1.2.0-RC.1

# This generates: target/jenkins-supabase-1.2.0-RC.1.hpi
```

## Best Practices

1. **Tag releases**: Always tag releases with `vX.Y.Z` format
2. **SNAPSHOT for development**: Keep SNAPSHOT suffix during development
3. **Semantic versioning**: 
   - Major: Breaking changes
   - Minor: New features (backward compatible)
   - Patch: Bug fixes (backward compatible)
4. **HPI naming**: The HPI file name remains consistent regardless of version