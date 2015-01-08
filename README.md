# downldr

Download all photos from all photo posts in a Tumblr blog.

## Build

You need Java 7 and Maven.

    $ mvn -Pdist package

will produce a dist package in `target/downldr-1.0-SNAPSHOT-dist.tar.gz`.

## Install

Just extract the dist package and add `<dist>/bin` to your `$PATH`.

Or to install from sources:

    $ mvn install -Dprefix=/your/install/path

where `/your/install/path/bin` is in your `$PATH`.

## Run

Provide the Tumblr blog name to the launch script.

    $ downldr garfieldminusgarfield.net

Photos will be saved in the current directory.
