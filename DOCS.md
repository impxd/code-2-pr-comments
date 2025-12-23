#### Get git remotes

```bash
$ git remote
```

#### Get git remote url

```bash
$ git remote get-url {remote}
```

#### Get current git branch name

```bash
$ git branch --show-current
```

#### Get GitHub PRs

The list will filter PRs by:
- Opened
- Author (same that sent the request)
- Branch name

```bash
$ gh pr list --state open --author "@me" --head "{branch name}" --json number,title,createdAt,headRefName
```

#### Get GitHub PR that belongs to the current branch 

This includes the last commit id

```bash
$ gh pr view --json headRefOid,number
```

#### Get the last commit id (local)

```bash
$ git rev-parse HEAD
```

#### Create a PR comment

Ref: https://docs.github.com/en/rest/pulls/comments?apiVersion=2022-11-28#create-a-review-comment-for-a-pull-request

```bash
$ gh api \
  --method POST \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  /repos/{owner}/{repo}/pulls/{pull_number}/comments \
   -f body=$'Multiple `lines` test 7\n2° line\n3° line' -f 'commit_id={commit_id}' -f 'path=test.clj' -F "line=26" -f 'side=RIGHT'
```

#### Get updated files in the current branch from main

```bash
$ git diff --name-only main...HEAD
```
