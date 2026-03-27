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

        jdk = pkgs.jdk21;

        coursierCache = squish-find-the-brains.lib.mkCoursierCache {
          inherit pkgs;
          lockfilePath = ./deps.lock.json;
        };

        sbtSetup = squish-find-the-brains.lib.mkSbtSetup {
          inherit pkgs coursierCache jdk;
        };
      in
      {
        packages = rec {
          magen = pkgs.stdenv.mkDerivation {
            inherit version;
            pname = "magen";
            src = ./.;

            nativeBuildInputs = sbtSetup.nativeBuildInputs ++ [ pkgs.makeWrapper ];
            inherit (sbtSetup) JAVA_HOME;

            buildPhase = ''
              export LANG=C.UTF-8
              export LC_ALL=C.UTF-8
              ${sbtSetup.setupScript}
              ${if pkgs.stdenv.isDarwin then ''
                HOME="$TMPDIR" \
                SBT_OPTS="-Duser.home=$TMPDIR -Dsbt.global.base=$TMPDIR/.sbt -Dsbt.ivy.home=$TMPDIR/.ivy2 -Divy.home=$TMPDIR/.ivy2 -Dsbt.boot.directory=$TMPDIR/.sbt/boot" \
                sbt assembly
              '' else ''
                sbt assembly
              ''}
            '';

            installPhase = ''
              mkdir -p $out/share/java $out/bin
              cp target/scala-2.13/magen.jar $out/share/java/

              makeWrapper ${jdk}/bin/java $out/bin/magen \
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
          ];

          JAVA_HOME = jdk;
        };
      }
    );
}
