# souther-maven-plugin

Compiles [Souther](https://github.com/souther-lang/souther) sources in a Maven build.

A project whose `src/main` holds only `.sou` used to compile nothing and build an empty jar,
reporting neither: the Souther annotation processor runs as part of `javac`, and `javac` does not
start a processing round when a compilation has no Java source. The way around it was a
`package-info.java` written in a language the project had chosen not to use. This plugin is what
replaces that.

## Use it

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.souther-lang</groupId>
      <artifactId>souther-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <goals><goal>compile</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

`.sou` under `src/main/souther` is compiled into `target/classes`, so the project's jar and its test
compilation read the generated classes without being told to, and Java written beside the model can
name it.

Your pom also declares the runtime that generated code calls:

```xml
<dependency>
  <groupId>org.souther-lang</groupId>
  <artifactId>souther-runtime</artifactId>
  <version>0.1.0-rc4</version>
</dependency>
```

The plugin checks that rather than adding it. What a plugin adds is not in the pom you publish, so
nothing depending on your project would get it — the failure would move downstream, to a build with
no Souther in it. If it is missing or is a different Souther, the build stops and says which version
to write.

Leave the scope out. A project depending on yours compiles against the classes your model generates,
and their signatures name the runtime; at `provided`, `test` or `runtime` scope it does not reach
that build, which is the failure this check exists to keep upstream.

## Importing another project's model

An ordinary dependency is all it takes. The module another project compiled is read from its
classes, so nothing has to be configured and no `.sou` is shared:

```text
module app.orders exposing ( Order )

import shared.money ( Amount )

data Order = { total: Amount }
```

## Choosing a Souther

A plugin release is verified against one Souther, and that is what a project naming no version gets.
To compile with another:

```xml
<configuration>
  <southerVersion>0.1.0-rc5</southerVersion>
</configuration>
```

or `-Dsouther.version=0.1.0-rc5` for one build.

The compiler is not on this plugin's class path. What the version names is
`org.souther-lang:souther-build-driver`, resolved from the repositories your project already uses
and run behind [`souther-build-api`](https://github.com/souther-lang/souther-build-api) in a class
loader of its own. So the plugin and the Souther it runs are released on their own terms: a Souther
release needs no plugin release unless the build protocol moves with it.

## Configuration

| Parameter | Property | Default |
|---|---|---|
| `sourceDirectories` | `souther.sourceDirectories` | `${project.basedir}/src/main/souther` |
| `southerVersion` | `souther.version` | the Souther this release was verified against |
| `languageTag` | `souther.lang` | what a command line naming none gets |

The goal is bound to `process-sources`, before `javac`, so that Java beside the model can name it.

## Design

[souther-lang/souther#137](https://github.com/souther-lang/souther/issues/137).

## License

Copyright © kawasima 2026

Released under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
