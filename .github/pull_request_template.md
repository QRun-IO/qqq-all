# Pull Request

## Description

**What does this PR do?**
A clear and concise description of what this pull request accomplishes.

**Related Issue:**
Closes #[issue_number]

## Type of Change

- [ ] **Bug Fix** - Fixes an existing issue
- [ ] **New Feature** - Adds new functionality
- [ ] **Breaking Change** - Fix or feature that would cause existing functionality to not work as expected
- [ ] **Documentation Update** - Updates documentation
- [ ] **Refactoring** - Code change that neither fixes a bug nor adds a feature
- [ ] **Build / CI** - Changes to the Maven build, CircleCI, compose, or image
- [ ] **Test Addition** - Adding missing tests or correcting existing tests

## Testing

**How has this been tested?**
- [ ] **Unit Tests**: All unit tests pass
- [ ] **Integration Tests**: Integration tests pass
- [ ] **Profiles**: Checked the affected profile(s) (`core`, `full`)
- [ ] **Manual Testing**: Tested manually in a development environment

**Test Commands:**
```bash
# Commands used for testing, and their results
mvn -B verify
```

## Checklist

**Before submitting this PR, please ensure:**

- [ ] **Code Style**: Follows QQQ's [Code Review Standards](https://github.com/QRun-IO/qqq/wiki/Code-Review-Standards)
- [ ] **Tests**: All tests pass
- [ ] **Documentation**: Updated the README or docs where behavior changed
- [ ] **Secrets**: No secrets committed; any demo credentials are marked demo-only and overridable by env
- [ ] **Breaking Changes**: Documented any breaking changes
- [ ] **Commit Messages**: Follow conventional commit format
- [ ] **Self Review**: Code has been reviewed by the author

## Additional Information

**Any additional information that reviewers should know:**
- Screenshots (if applicable)
- Dependencies added/removed
- Migration notes
