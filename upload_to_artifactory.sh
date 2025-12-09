#!/bin/bash

# can't get mvn deploy to work for some reason
# surely there's a better way?

VERSION="0.4.0-atlassian-1"
GROUPID="org.apache.xtable"

ARTIFACTID="xtable-api"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$ARTIFACTID/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$ARTIFACTID/target/$ARTIFACTID-$VERSION-sources.jar

ARTIFACTID="xtable-core_2.12"
PATH_PREFIX="xtable-core"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$PATH_PREFIX/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$PATH_PREFIX/target/$ARTIFACTID-$VERSION-sources.jar

ARTIFACTID="xtable-aws"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$ARTIFACTID/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$ARTIFACTID/target/$ARTIFACTID-$VERSION-sources.jar

ARTIFACTID="xtable-hive-metastore"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$ARTIFACTID/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$ARTIFACTID/target/$ARTIFACTID-$VERSION-sources.jar


ARTIFACTID="xtable-service"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$ARTIFACTID/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$ARTIFACTID/target/$ARTIFACTID-$VERSION-sources.jar


ARTIFACTID="xtable-utilities_2.12"
PATH_PREFIX="xtable-utilities"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$PATH_PREFIX/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$PATH_PREFIX/target/$ARTIFACTID-$VERSION-sources.jar


ARTIFACTID="xtable-hudi-support-utils"
PATH_PREFIX="xtable-hudi-support/xtable-hudi-support-utils"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$PATH_PREFIX/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$PATH_PREFIX/target/$ARTIFACTID-$VERSION-sources.jar


ARTIFACTID="xtable-hudi-support-extensions_2.12"
PATH_PREFIX="xtable-hudi-support/xtable-hudi-support-extensions"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$PATH_PREFIX/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$PATH_PREFIX/target/$ARTIFACTID-$VERSION-sources.jar

ARTIFACTID="xtable-hudi-support-utils"
PATH_PREFIX="xtable-hudi-support/xtable-hudi-support-utils"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$PATH_PREFIX/target/$ARTIFACTID-$VERSION.jar \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=jar \
                       -DgeneratePom=true \
                       -Dsources=$PATH_PREFIX/target/$ARTIFACTID-$VERSION-sources.jar

ARTIFACTID="xtable-hudi-support"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=$ARTIFACTID/pom.xml \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=po

ARTIFACTID="xtable"
mvn deploy:deploy-file -Durl=https://packages.atlassian.com/maven/3rdparty \
                       -DrepositoryId=atlassian-3rdparty \
                       -Dfile=pom.xml \
                       -DgroupId=$GROUPID \
                       -DartifactId=$ARTIFACTID \
                       -Dversion=$VERSION \
                       -Dpackaging=pom