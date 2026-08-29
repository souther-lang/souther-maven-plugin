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
      <version>0.2.0</version>
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
  <version>0.1.0</version>
</dependency>
```

The plugin reads that rather than adding it. What a plugin adds is not in the pom you publish, so
nothing depending on your project would get it — the failure would move downstream, to a build with
no Souther in it. Since your pom names the runtime, that declaration is the Souther your model is
compiled with: generated code calls the runtime of the Souther that produced it, so there is one
version and one place it is written. A pom that declares no runtime is stopped here rather than
downstream.

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

The runtime dependency is the choice. Move its version and the next build compiles with that
Souther. Writing it through a property of your own is what makes it one line for a reactor, and what
lets one build name another Souther:

```xml
<properties>
  <souther.version>0.1.0</souther.version>
</properties>

<dependency>
  <groupId>org.souther-lang</groupId>
  <artifactId>souther-runtime</artifactId>
  <version>${souther.version}</version>
</dependency>
```

```bash
mvn -Dsouther.version=0.1.1-SNAPSHOT test
```

That property is your pom's. This plugin has no parameter of its own for the version: a second place
to say it is a second answer to give when the two disagree.

The compiler is not on this plugin's class path. What the version names is
`org.souther-lang:souther-build-driver`, resolved from the repositories your project already uses
and run behind [`souther-build-api`](https://github.com/souther-lang/souther-build-api) in a class
loader of its own. So the plugin and the Souther it runs are released on their own terms, and this
one names no Souther anywhere: there is no default to go stale, and a release of this plugin is for
this plugin or for the build protocol moving.

## Configuration

| Parameter | Property | Default |
|---|---|---|
| `sourceDirectories` | `souther.sourceDirectories` | `${project.basedir}/src/main/souther` |
| `languageTag` | `souther.lang` | what a command line naming none gets |

The goal is bound to `process-sources`, before `javac`, so that Java beside the model can name it.

## Design

[souther-lang/souther#137](https://github.com/souther-lang/souther/issues/137).

## License

Copyright © kawasima 2026

Released under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
