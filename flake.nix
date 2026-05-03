{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    flake-compat.url = "github:NixOS/flake-compat";
    flake-parts.url = "github:hercules-ci/flake-parts";
    make-shell = {
      url = "github:nicknovitski/make-shell";
      inputs.flake-compat.follows = "flake-compat";
    };
    nix2container.url = "github:nlewo/nix2container";
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };
  outputs = inputs @ {flake-parts, ...}:
    flake-parts.lib.mkFlake {inherit inputs;} ({...}: {
      imports = [
        inputs.make-shell.flakeModules.default
        inputs.treefmt-nix.flakeModule
        ./nix/packages.nix
        ./nix/module.nix
      ];
      systems = ["x86_64-linux"]; # TODO add more when i have a way to test them.
      perSystem.treefmt = {
        # Used to find the project root
        projectRootFile = "flake.nix";

        programs = {
          keep-sorted.enable = true;

          # Github Actions
          actionlint.enable = true;
          zizmor.enable = true;

          # Enable the Nix formatter
          alejandra.enable = true;
          deadnix.enable = true;
          nixf-diagnose.enable = true;
          statix.enable = true;

          # Java
          google-java-format.enable = false; # Currently causes lots of reformatting

          # Protobuf
          buf.enable = false; # Wants to rename several things
          protolint.enable = false; # Wants to rename several things
        };
      };
    });
}
