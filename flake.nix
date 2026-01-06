{
  description = "magen - keyboard mapping generator";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  inputs.squish-find-the-brains.url = "github:7mind/squish-find-the-brains";
  inputs.squish-find-the-brains.inputs.nixpkgs.follows = "nixpkgs";
  inputs.squish-find-the-brains.inputs.flake-utils.follows = "flake-utils";

  outputs =
    { self
    , nixpkgs
    , flake-utils
    , squish-find-the-brains
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };

        jdk = pkgs.jdk21;

        coursierCache = squish-find-the-brains.lib.mkCoursierCache {
          inherit pkgs;
          lockfilePath = ./deps.lock.json;
        };

        sbtSetup = squish-find-the-brains.lib.mkSbtSetup {
          inherit pkgs coursierCache;
          jdk = jdk;
        };
      in
      {
        packages = rec {
          magen = pkgs.stdenv.mkDerivation {
            pname = "magen";
            version = "0.1.0";
            src = ./.;

            nativeBuildInputs = sbtSetup.nativeBuildInputs ++ [ pkgs.makeWrapper ];
            inherit (sbtSetup) JAVA_HOME;

            buildPhase = ''
              export LANG=C.UTF-8
              export LC_ALL=C.UTF-8
              ${sbtSetup.setupScript}
              sbt assembly
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

        devShells.default = pkgs.mkShell {
          nativeBuildInputs = with pkgs; [
            jdk
            sbt
            gitMinimal
            coreutils
            coursier
            squish-find-the-brains.packages.${system}.generate-lockfile
          ];

          JAVA_HOME = jdk;
        };
      }
    );
}
