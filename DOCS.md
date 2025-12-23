#### Get git remotes

```bash
$ git remote
```

#### Get git remote url

```bash
$ git remote get-url <remote>
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
$ gh pr list --state open --author "@me" --head "<branch name>" --json number,title,createdAt,headRefName
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
