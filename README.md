# SharedLibrary plugin

This plugin is meant to provide missing functionality of adding
specific directory from within repository as shared pipeline
library.

### Usage

1. Build plugin using `mvn package`
2. Install plugin to your Jenkins instance
3. Setup a pipeline job with pipeline script from SCM - it's
important to unmark lightweight checkout - we need whole
repository in place
    * whenever your pipeline is started the whole repository is
    checked out to directory `$JENKINS_HOME/workspace/$FULL_JOB_NAME@script`
    * SharedLibrary plugin uses this location (substituting 
    `/var/jenkins_home` for `JENKINS_HOME`) to load specific directory
    to classpath
4. How to use specific directory as shared library
    
    Simply put 
    ```groovy
    @SharedLibrary('dir_in_repo') _
    ```
    to your pipeline. It'll then load content of `dir_in_repo`
    to classpath every time your pipeline is ran.
    You can use import statements as if `dir_in_repo` would
    be groovy sources root
    
### Identified problems

Right now plugin depends on `JENKINS_HOME` directory to be set to
`/var/jenkins_home` which may not be the case for all Jenkins
instances. Please feel free to contribute if you have an idea
on how to get that per-instance variable properly.