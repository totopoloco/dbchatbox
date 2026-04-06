# dbchatbox

## How to Import Your Local Git Project into This Repository

Follow the steps below to push your existing local Git project to this GitHub repository.

---

### Option A — Your local project is already a Git repository

If you have already run `git init` in your local project directory:

```bash
# 1. Navigate into your local project folder
cd /path/to/your/local/project

# 2. Add this repository as the remote origin
git remote add origin https://github.com/totopoloco/dbchatbox.git

# 3. Rename your current branch to main (if it isn't already)
git branch -M main

# 4. Push your code (the -u flag sets the upstream tracking branch)
git push -u origin main
```

> **Tip:** If the repository already has commits (e.g. the default `README.md` or `LICENSE`), you may need to reconcile the histories first:
>
> ```bash
> git pull origin main --allow-unrelated-histories
> # resolve any merge conflicts, then:
> git push -u origin main
> ```

---

### Option B — Your local project is NOT yet a Git repository

If you have a folder of source files but have not run `git init` yet:

```bash
# 1. Navigate into your local project folder
cd /path/to/your/local/project

# 2. Initialise a new Git repository
git init

# 3. Stage all existing files
git add .

# 4. Create an initial commit
git commit -m "Initial commit"

# 5. Add this repository as the remote origin
git remote add origin https://github.com/totopoloco/dbchatbox.git

# 6. Rename the branch to main
git branch -M main

# 7. Push to GitHub
git push -u origin main
```

---

### Using SSH instead of HTTPS

If you prefer SSH authentication, replace the remote URL in the commands above:

```bash
git remote add origin git@github.com:totopoloco/dbchatbox.git
```

---

### Verify the import

After pushing, open your browser and visit:

```
https://github.com/totopoloco/dbchatbox
```

You should see all your files listed in the repository.

---

### Prerequisites

| Requirement | Notes |
|---|---|
| [Git](https://git-scm.com/downloads) installed locally | Version 2.x or newer recommended |
| A GitHub account with write access to this repository | Contact the repository owner if you need access |
| (HTTPS) A [personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) or GitHub CLI configured | Required when password authentication is disabled |
| (SSH) An [SSH key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh) added to your GitHub account | Required for SSH-based pushes |