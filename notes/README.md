# Table of Contents

1.  [The Guide](#orgda6c48c)
2.  [Editing these files](#orgd7552f5)


<a id="orgda6c48c"></a>

# The Guide

This directory contains some brief jottings on the codebase and
architecture. It is intended for maintainers, though some parts may be
useful for frequent contributors.

-   **architecture.md** contains an overview of the cloud architecture
-   **deployment.md** contains notes for maintainers on deployment


<a id="orgd7552f5"></a>

# Editing these files

These files have been written in Emacs org-mode and converted into
markdown for easy viewing.
The markdown export is done from Emacs 27.1 using
`org-md-export-to-markdown`, configured with the following functions:

    (defun lsug-add-backticks (data backend plist)
      (concat "```\n" (org-remove-indentation (string-trim data)) "\n```\n\n"))

    (add-to-list 'org-export-filter-example-block-functions 'lsug-add-backticks)
    (add-to-list 'org-export-filter-src-block-functions 'lsug-add-backticks)
