# Release Process

1. commit all changes to `develop` or feature branch
2. `git tag v?.?`
3. `./gradlew assembleDist`
4. `mv build/distributions/???.tar nicername.tar`
5. use nicername.tar for new github release
6. update Dockerfile to use new release
7. commit
8. merge to master
