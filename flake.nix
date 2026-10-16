{
  description = "Dev shell for Otter's AE2 Recursive Patterns (NeoForge 26.1.2 / Java 25)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};

      # Gradle 8.14.3 (the wrapper's version) cannot run on JDK 25, so it is
      # launched on JDK 21 and compiles through the Java 25 toolchain.
      launcherJdk = pkgs.jdk21;
      toolchainHome = "${pkgs.jdk25}/lib/openjdk";

      # Only needed for runClient (Minecraft's native libraries).
      runtimeLibs = with pkgs; [ libGL glfw openal libpulseaudio xorg.libX11 xorg.libXcursor xorg.libXrandr xorg.libXxf86vm ];
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ launcherJdk ];

        JAVA_HOME = "${launcherJdk}/lib/openjdk";
        LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs;

        # Keep a repo-local Gradle home (already gitignored) so the toolchain
        # settings below do not touch the user's global ~/.gradle. GRADLE_USER_HOME
        # is set in the hook so it resolves to the working tree, not the read-only
        # store copy that `nix develop path:.` makes.
        #
        # Gradle only reads org.gradle.java.installations.* from gradle.properties
        # (not from GRADLE_OPTS), so point it at the Nix JDK 25 and disable the
        # foojay auto-download, whose JDKs don't run on NixOS.
        shellHook = ''
          export GRADLE_USER_HOME="''${GRADLE_USER_HOME:-$PWD/.gradle}"
          mkdir -p "$GRADLE_USER_HOME"
          printf 'org.gradle.java.installations.auto-download=false\norg.gradle.java.installations.paths=%s\n' \
            '${toolchainHome}' > "$GRADLE_USER_HOME/gradle.properties"
        '';
      };
    };
}
