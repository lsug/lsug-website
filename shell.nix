{ pkgs ? import <nixpkgs> {} }:
with pkgs;
mkShell {

  # Require yarn, and nodejs for the client builds
  buildInputs = [ mill nodejs yarn ammonite ];

  # Some useful aliases for the shell
  shellHook = ''
            function bundle {
              mill web.bundle
            }
            function compile {
              mill server.compile
              mill client.compile
            }
            function reformat {
              mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
            }
            function run {
             mill web.run
           }
    '';

}
