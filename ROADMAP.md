# Jenkins Supabase Plugin - Roadmap

This document outlines the planned features and extensions for the Jenkins Supabase Plugin.

## Current Features (v1.x)

### Event Triggers ✅
- Real-time database event monitoring (INSERT, UPDATE, DELETE)
- Multiple Supabase instance support
- Flexible table selection with schema support
- Event data as environment variables
- Secure credential management

## Planned Features

### Phase 2: Post-Build Actions (v2.0)

#### Database Operations
- **Insert Records**: Add data to tables after successful builds
- **Update Records**: Modify existing records based on build results
- **Delete Records**: Remove records conditionally
- **Custom SQL Execution**: Run arbitrary SQL queries
- **Batch Operations**: Execute multiple operations atomically

#### Example Use Cases
```groovy
pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                // Deployment steps
            }
            post {
                success {
                    supabaseInsert table: 'deployments', 
                                  data: [
                                      environment: 'production',
                                      version: env.BUILD_NUMBER,
                                      status: 'success',
                                      deployed_at: 'now()'
                                  ]
                }
                failure {
                    supabaseUpdate table: 'deployments',
                                  where: [build_id: env.BUILD_ID],
                                  data: [status: 'failed']
                }
            }
        }
    }
}
```

### Phase 3: Storage Integration (v2.1)

#### File Operations
- **Upload Build Artifacts**: Store build outputs in Supabase Storage
- **Download Dependencies**: Fetch files from storage buckets
- **Backup Operations**: Automated backup of build artifacts
- **File Cleanup**: Remove old artifacts based on retention policies

#### Example Configuration
```groovy
supabaseUpload bucket: 'build-artifacts',
              file: 'target/app.jar',
              path: "releases/${env.BUILD_NUMBER}/app.jar",
              public: false
```

### Phase 4: Advanced Features (v2.2)

#### Row-Level Security (RLS)
- **Policy Management**: Create/update RLS policies through Jenkins
- **Role-Based Access**: Manage database permissions
- **Security Auditing**: Log access control changes

#### Edge Functions
- **Function Deployment**: Deploy Deno functions to Supabase Edge
- **Function Testing**: Run tests against deployed functions
- **Function Monitoring**: Monitor function performance and errors

#### Auth Integration
- **User Management**: Create/update user accounts
- **JWT Token Management**: Generate and validate tokens
- **OAuth Integration**: Manage third-party auth providers

### Phase 5: Monitoring & Analytics (v3.0)

#### Real-time Dashboards
- **Build Metrics**: Database-driven build analytics
- **Performance Monitoring**: Track database operation performance
- **Error Tracking**: Comprehensive error logging and alerting

#### Reporting
- **Custom Reports**: Generate reports from database data
- **Automated Insights**: AI-powered build and deployment insights
- **Compliance Reporting**: Audit trail and compliance reports

## Configuration Evolution

### Global Configuration Extensions
```xml
<!-- Future global configuration -->
<supabaseInstances>
    <instance>
        <name>production</name>
        <url>https://prod.supabase.co</url>
        <credentials>prod-api-key</credentials>
        <features>
            <triggers>true</triggers>
            <postActions>true</postActions>
            <storage>true</storage>
            <edgeFunctions>true</edgeFunctions>
        </features>
        <storage>
            <buckets>
                <bucket name="build-artifacts" policy="private"/>
                <bucket name="public-assets" policy="public"/>
            </buckets>
        </storage>
    </instance>
</supabaseInstances>
```

### Job Configuration Extensions
```groovy
pipeline {
    agent any
    
    // Event triggers (current)
    triggers {
        supabaseEvent instances: ['production'],
                     tables: ['deployment_requests'],
                     events: ['INSERT']
    }
    
    stages {
        stage('Build') {
            steps {
                // Build steps
            }
        }
        
        stage('Test') {
            steps {
                // Test steps with Supabase data
                supabaseQuery instance: 'production',
                             query: 'SELECT * FROM test_data WHERE active = true',
                             variable: 'TEST_DATA'
            }
        }
    }
    
    post {
        success {
            // Post-build actions (planned)
            supabaseInsert instance: 'production',
                          table: 'deployments',
                          data: [
                              version: env.BUILD_NUMBER,
                              status: 'success'
                          ]
                          
            supabaseUpload instance: 'production',
                          bucket: 'build-artifacts',
                          file: 'target/*.jar'
        }
    }
}
```

## Plugin Architecture Evolution

### Current Architecture
```
Jenkins Job → Event Trigger → Supabase Realtime WebSocket
```

### Future Architecture
```
Jenkins Job ←→ Supabase Plugin Core ←→ Multiple Supabase Services
                      ↓
                 ┌─────────────┬─────────────┬─────────────┐
                 │  Realtime   │  Database   │   Storage   │
                 │  WebSocket  │    REST     │    REST     │
                 └─────────────┴─────────────┴─────────────┘
                                     │
                            ┌────────┴────────┐
                            │  Edge Functions │
                            │      REST       │
                            └─────────────────┘
```

## Migration Strategy

### Backward Compatibility
- All v1.x trigger configurations will continue to work
- Gradual introduction of new features with feature flags
- Clear migration guides for each major version

### Configuration Migration
- Automatic migration of existing trigger configurations
- Optional migration to new unified configuration format
- Support for mixed old/new configurations during transition

## Implementation Timeline

| Phase | Version | Target Release | Key Features |
|-------|---------|----------------|--------------|
| 2 | v2.0 | Q1 2026 | Post-build actions, database operations |
| 3 | v2.1 | Q2 2026 | Storage integration |
| 4 | v2.2 | Q3 2026 | RLS, Edge Functions, Auth |
| 5 | v3.0 | Q4 2026 | Monitoring, analytics, dashboards |

## Contribution Opportunities

Developers interested in contributing can focus on:

1. **Database Operations**: Implementing SQL execution and CRUD operations
2. **Storage Integration**: File upload/download functionality
3. **Edge Functions**: Deno function deployment and testing
4. **UI/UX**: Improving configuration interfaces
5. **Documentation**: User guides and examples
6. **Testing**: Comprehensive test coverage for new features

## Getting Involved

- 📧 **Discussions**: GitHub Discussions for feature requests
- 🐛 **Issues**: GitHub Issues for bug reports and feature suggestions
- 🔧 **Pull Requests**: Code contributions welcome
- 📖 **Documentation**: Help improve user guides and examples

---

*This roadmap is subject to change based on community feedback and evolving requirements.*