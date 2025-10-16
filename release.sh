#!/bin/bash

# Jenkins Supabase Plugin Release Script
# This script helps automate the release process

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
print_info() {
    echo -e "${BLUE}INFO:${NC} $1"
}

print_success() {
    echo -e "${GREEN}SUCCESS:${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}WARNING:${NC} $1"
}

print_error() {
    echo -e "${RED}ERROR:${NC} $1"
}

# Function to check if we're on main branch
check_main_branch() {
    local branch=$(git branch --show-current)
    if [ "$branch" != "main" ]; then
        print_error "Must be on main branch for release. Current branch: $branch"
        exit 1
    fi
}

# Function to check if working directory is clean
check_clean_working_dir() {
    if [ -n "$(git status --porcelain)" ]; then
        print_error "Working directory is not clean. Commit or stash changes first."
        git status
        exit 1
    fi
}

# Function to validate version format
validate_version() {
    local version=$1
    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        print_error "Invalid version format: $version. Use semantic versioning (e.g., 1.1.0)"
        exit 1
    fi
}

# Function to check if tag already exists
check_tag_exists() {
    local tag=$1
    if git rev-parse "$tag" >/dev/null 2>&1; then
        print_error "Tag $tag already exists"
        exit 1
    fi
}

# Function to build and test
build_and_test() {
    print_info "Building and testing plugin..."
    mvn clean verify
    print_success "Build and tests completed successfully"
}

# Function to create release
create_release() {
    local version=$1
    local tag="v$version"
    
    print_info "Creating release $version"
    
    # Check prerequisites
    check_main_branch
    check_clean_working_dir
    validate_version "$version"
    check_tag_exists "$tag"
    
    # Build and test
    build_and_test
    
    # Create and push tag
    print_info "Creating tag $tag"
    git tag -a "$tag" -m "Release version $version"
    
    print_info "Pushing tag to origin"
    git push origin "$tag"
    
    # Build release artifact
    print_info "Building release artifact"
    mvn clean package -DskipTests
    
    # Display artifact info
    local hpi_file="target/jenkins-supabase.hpi"
    if [ -f "$hpi_file" ]; then
        local file_size=$(ls -lh "$hpi_file" | awk '{print $5}')
        print_success "Release artifact created: $hpi_file ($file_size)"
        print_info "MD5: $(md5sum "$hpi_file" | awk '{print $1}')"
        print_info "SHA256: $(sha256sum "$hpi_file" | awk '{print $1}')"
    else
        print_error "Failed to create HPI file"
        exit 1
    fi
    
    print_success "Release $version completed successfully!"
    print_info "Next steps:"
    print_info "1. Upload $hpi_file to Jenkins Update Center or install manually"
    print_info "2. Update CHANGELOG.md with release notes"
    print_info "3. Consider creating a GitHub release with the HPI file"
}

# Function to show current version
show_version() {
    local current_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
    print_info "Current version: $current_version"
    
    local latest_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "No tags found")
    print_info "Latest tag: $latest_tag"
}

# Function to build snapshot
build_snapshot() {
    print_info "Building snapshot version"
    mvn clean package -DskipTests
    
    local hpi_file="target/jenkins-supabase.hpi"
    if [ -f "$hpi_file" ]; then
        local file_size=$(ls -lh "$hpi_file" | awk '{print $5}')
        print_success "Snapshot artifact created: $hpi_file ($file_size)"
    else
        print_error "Failed to create HPI file"
        exit 1
    fi
}

# Function to show help
show_help() {
    echo "Jenkins Supabase Plugin Release Script"
    echo ""
    echo "Usage: $0 <command> [arguments]"
    echo ""
    echo "Commands:"
    echo "  version                 Show current version and latest tag"
    echo "  build                   Build snapshot version"
    echo "  test                    Run tests"
    echo "  release <version>       Create a release (e.g., release 1.1.0)"
    echo "  help                    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 version"
    echo "  $0 build"
    echo "  $0 test"
    echo "  $0 release 1.1.0"
}

# Main script logic
case "${1:-help}" in
    "version")
        show_version
        ;;
    "build")
        build_snapshot
        ;;
    "test")
        print_info "Running tests..."
        mvn clean test
        print_success "Tests completed"
        ;;
    "release")
        if [ -z "$2" ]; then
            print_error "Version argument required for release command"
            echo "Usage: $0 release <version>"
            exit 1
        fi
        create_release "$2"
        ;;
    "help"|"--help"|"-h")
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac