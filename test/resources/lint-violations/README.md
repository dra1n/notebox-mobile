Deliberate dependency-rule violations. `notebox.lint-rules-test` runs `check-deps`
on this directory and expects exactly the problems listed there. These files are
never compiled; they only need valid `ns` forms.
