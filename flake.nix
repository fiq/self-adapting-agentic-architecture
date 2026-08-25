{
  description = "self-adapting-agentic-architecture development shell and checks";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f:
        builtins.listToAttrs (map (system: {
          name = system;
          value = f system;
        }) systems);
      saaaRunner = system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
          pkgs.writeShellApplication {
            name = "saaa";
            runtimeInputs = [
              pkgs.git
              pkgs.gradle_9
              pkgs.jdk25_headless
              pkgs.jq
              pkgs.docker-client
              pkgs.docker-compose
              pkgs.python3
            ];
            text = ''
              task_root="''${SAAA_SOURCE_ROOT:-$PWD}"
              if [[ ! -x "$task_root/.agentic-template/bin/gradle-command" ]]; then
                echo "saaa: run from a SAAA checkout or set SAAA_SOURCE_ROOT" >&2
                exit 2
              fi
              "$task_root/.agentic-template/bin/gradle-command" --quiet :cli:installDist
              exec "$task_root/modules/cli/build/install/saaa/bin/saaa" "$@"
            '';
          };
    in {
      packages = forAllSystems (system: {
        default = saaaRunner system;
        saaa = saaaRunner system;
      });

      apps = forAllSystems (system: {
        default = {
          type = "app";
          program = "${saaaRunner system}/bin/saaa";
          meta.description = "Run the local SAAA Java CLI";
        };
        saaa = {
          type = "app";
          program = "${saaaRunner system}/bin/saaa";
          meta.description = "Run the local SAAA Java CLI";
        };
      });

      checks = forAllSystems (system: {
        repo-contract =
          let
            pkgs = nixpkgs.legacyPackages.${system};
          in
            pkgs.runCommand "repo-contract" {
              nativeBuildInputs = [ pkgs.bash pkgs.python3 ];
            } ''
              cp -r ${self} source
              chmod -R u+w source
              cd source
              patchShebangs .agentic-template/bin
              .agentic-template/bin/project check
              touch $out
            '';
      });

      devShells = forAllSystems (system: {
        default =
          let
            pkgs = nixpkgs.legacyPackages.${system};
          in
          pkgs.mkShell {
            packages = [
              pkgs.git
              pkgs.gradle_9
              pkgs.jdk25_headless
              pkgs.jq
              pkgs.docker-client
              pkgs.docker-compose
              pkgs.python3
              pkgs.ripgrep
            ];

          shellHook = ''
            echo "self-adapting-agentic-architecture shell: use .agentic-template/bin/project help"
            echo "Optional: .agentic-template/bin/project install-hooks (non-blocking wiki-drift pre-commit)"
          '';
          };
      });
    };
}
