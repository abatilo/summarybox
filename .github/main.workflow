workflow "Publish new summarybox" {
  on = "push"
  resolves = ["Push container"]
}

action "Master branch" {
  uses = "actions/bin/filter@master"
  args = "branch master"
}

action "Push container" {
  needs = ["Master branch"]
  uses = "./.github/make/"
  secrets = ["REGISTRY_USER", "REGISTRY_PASS", "REGISTRY_URL"]
  args = ["push"]
}
