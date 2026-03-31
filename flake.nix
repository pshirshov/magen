{
  description = "magen - keyboard mapping generator";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  inputs.squish-find-the-brains.url = "github:7mind/squish-find-the-brains";
  inputs.squish-find-the-brains.inputs.nixpkgs.follows = "nixpkgs";
  inputs.squish-find-the-brains.inputs.flake-utils.follows = "flake-utils";

  inputs.mudyla.url = "github:7mind/mudyla";
  inputs.mudyla.inputs.nixpkgs.follows = "nixpkgs";

  outputs =
    { self
    , nixpkgs
    , flake-utils
    , squish-find-the-brains
    , mudyla
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };

        buildSbt = builtins.readFile ./build.sbt;
        versionMatch = builtins.match ''.*version := "([0-9]+\.[0-9]+\.[0-9]+)(-SNAPSHOT)?".*'' buildSbt;
        version = builtins.elemAt versionMatch 0;

        jdk = pkgs.graalvmPackages.graalvm-ce;

        coursierCache = squish-find-the-brains.lib.mkCoursierCache {
          inherit pkgs;
          lockfilePath = ./deps.lock.json;
        };

        sbtSetup = squish-find-the-brains.lib.mkSbtSetup {
          inherit pkgs coursierCache jdk;
        };

        # AWT runtime libraries (X11, fonts, rendering)
        awtRuntimeLibs = with pkgs; [
          xorg.libX11
          xorg.libXtst
          xorg.libXrender
          xorg.libXext
          xorg.libXi
          xorg.libXt
          xorg.libXrandr
          xorg.libXcursor
          xorg.libXinerama
          xorg.libXfixes
          freetype
          fontconfig
          zlib
        ];
      in
      {
        packages = rec {
          magen = pkgs.stdenv.mkDerivation {
            inherit version;
            pname = "magen";
            src = ./.;

            nativeBuildInputs = sbtSetup.nativeBuildInputs ++ [
              pkgs.makeWrapper
              pkgs.zip
              pkgs.unzip
            ];
            inherit (sbtSetup) JAVA_HOME;

            buildPhase = ''
              export LANG=C.UTF-8
              export LC_ALL=C.UTF-8
              ${sbtSetup.setupScript}
              ${if pkgs.stdenv.isDarwin then ''
                HOME="$TMPDIR" \
                SBT_OPTS="-Duser.home=$TMPDIR -Dsbt.global.base=$TMPDIR/.sbt -Dsbt.ivy.home=$TMPDIR/.ivy2 -Divy.home=$TMPDIR/.ivy2 -Dsbt.boot.directory=$TMPDIR/.sbt/boot" \
                sbt assembly packageDataZip
              '' else ''
                sbt assembly packageDataZip
              ''}

              # Build native image from the assembly JAR
              native-image \
                --no-fallback \
                "--initialize-at-build-time=scala,io.septimalmind,io.circe,cats,shapeless,izumi,org.snakeyaml,org.yaml,org.typelevel,jawn,macrocompat,io.github" \
                -H:+ReportExceptionStackTraces \
                -H:+AddAllCharsets \
                -jar target/scala-2.13/magen.jar \
                -o magen-native
            '';

            installPhase = ''
              mkdir -p $out/bin $out/share/magen $out/share/java

              # Install native binary
              cp magen-native $out/bin/magen-native

              # Install assembly JAR as fallback
              cp target/scala-2.13/magen.jar $out/share/java/

              # Extract data zip into share/magen/
              unzip -o target/magen-data.zip -d $out/share/magen/

              # Install data zip alongside the binary
              cp target/magen-data.zip $out/share/magen/

              # Create wrapper for native binary that sets MAGEN_DATA_DIR
              makeWrapper $out/bin/magen-native $out/bin/magen \
                --set MAGEN_DATA_DIR $out/share/magen

              # Also create a JVM wrapper for compatibility
              makeWrapper ${jdk}/bin/java $out/bin/magen-jvm \
                --add-flags "-jar $out/share/java/magen.jar"
            '';
          };
          default = magen;
        };

        apps = {
          magen = {
            type = "app";
            program = "${self.packages.${system}.magen}/bin/magen";
          };
          default = self.apps.${system}.magen;
        };

        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            jdk
            sbt
            gitMinimal
            coreutils
            coursier

            nix

            squish-find-the-brains.packages.${system}.generate-lockfile
            mudyla.packages.${system}.default
          ] ++ awtRuntimeLibs;

          JAVA_HOME = jdk;

          shellHook = ''
            export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath awtRuntimeLibs}:$LD_LIBRARY_PATH"
            export FONTCONFIG_PATH="${pkgs.fontconfig.out}/etc/fonts"
          '';
        };
      }
    );
}
