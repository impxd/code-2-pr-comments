(ns main
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.data.json :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(defn fetch-gh-pr-from-current-wd
  "Fetches the GitHub PR data associated with the current working directory."
  []
  (let [result (process/shell {:out :string} "gh" "pr" "view" "--json" "baseRefName,headRefName,headRefOid,headRepository,headRepositoryOwner,number")]
    (-> result :out (json/read-str :key-fn keyword))))

(defn modified-git-files
  "Returns a list of modified files in the current git working directory."
  [from to]
  (let [result (process/shell {:out :string :err :out :continue true} "git" "diff" "--name-only" (str from "..." to))]
    (-> result :out clojure.string/split-lines)))

(defn grep-line-output-to-filename
  "Extracts filename from grep output line."
  [grep-output-line]
  (first (clojure.string/split grep-output-line #":")))

(defn match-files-with-tagged-comments
  "Returns a list of files in the given directory that contain comments marked
   with the specified comment marker."
  [files]
  (if (empty? files)
    []
    (->> (apply process/shell {:out :string :err :out :continue true} "grep" "-H" "; 💬 " files)
         :out
         (clojure.string/split-lines)
         (map grep-line-output-to-filename)
         set
         vec)))

(defn extract-tagged-comments [source-code]
  (let [lines (str/split-lines source-code)
        ;; Map lines to valid index (1-based)
        numbered-lines (map-indexed (fn [i l] [(inc i) l]) lines)]

    (:results
     (reduce
      (fn [{:keys [in-block? buffer results] :as state} [line-num line-text]]
        (let [trimmed (str/trim line-text)
              ;; Detect if line is a comment (ignoring indentation)
              is-comment? (str/starts-with? trimmed ";")
              ;; Extract text content: Remove leading semicolons and spaces
              content (when is-comment?
                        (str/replace trimmed #"^;+\s*" ""))]

          (cond
            ;; Case 1: Start of a specific 💬 block
            (and is-comment? (str/starts-with? content "💬"))
            (let [clean-content (-> content
                                    (str/replace-first "💬" "")
                                    (str/trim))]
              (assoc state
                     :in-block? true
                     :buffer [clean-content]))

            ;; Case 2: Continuation of a block (subsequent comment lines)
            (and is-comment? in-block?)
            (update state :buffer conj content)

            ;; Case 3: Code line immediately following a block
            (and (not is-comment?) in-block?)
            {:in-block? false
             :buffer []
             :results (conj results {:line line-num
                                     :comment (str/join "\n" buffer)})}

            ;; Case 4: Reset if we hit a non-comment and weren't in a block, 
            ;; or irrelevant comments. Keep state as is.
            :else
            state)))

      ;; Initial State
      {:in-block? false :buffer [] :results []}
      numbered-lines))))

(defn files-with-tagged-comments-message
  "Formats a message for a file with tagged comments."
  [file-with-tagged-comments]
  (str
   "File: " (:filename file-with-tagged-comments) "\n"
   (str/join
    "\n"
    (map (fn [comment]
           (str " " (:line comment) ": " (:comment comment)))
         (:tagged-comments file-with-tagged-comments)))))

(defn create-gh-pr-comment
  "Creates a PR comment from the extracted tagged comments."
  [filename comment pr-data]
  (let [pr-number (str (:number pr-data))
        body (:comment comment)
        line (str (:line comment))]
    (process/shell
     {:out :string}
     "gh" "api"
     "--method" "POST"
     "-H" "Accept: application/vnd.github+json"
     "-H" "X-GitHub-Api-Version: 2022-11-28"
     (str "/repos/"
          (get-in pr-data [:headRepositoryOwner :login])
          "/"
          (get-in pr-data [:headRepository :name])
          "/pulls/"
          pr-number
          "/comments")
     "-f" (str "body=" body)
     "-f" (str "commit_id=" (:headRefOid pr-data))
     "-f" (str "path=" filename)
     "-F" (str "line=" line)
     "-f" "side=RIGHT")))

(defn -main [opts]
  (let [pr-data (fetch-gh-pr-from-current-wd)
        modified-files (modified-git-files #_"main" (:baseRefName pr-data) (:headRefOid pr-data))
        files-with-comments (match-files-with-tagged-comments #_["test.clj"] modified-files)
        files-with-tagged-comments (map (fn [filename]
                                      {:filename filename
                                       :tagged-comments (extract-tagged-comments (slurp filename))})
                                    files-with-comments)]
    (println "PR data:")
    (println pr-data)
    (println "\nModified files:")
    (doseq [f modified-files]
      (println f))
    (println "\nFiles with '💬' comments:")
    (doseq [f files-with-comments]
      (println f))
    (println "\nExtracted tagged comments from files:")
    (doseq [item files-with-tagged-comments]
      (println (files-with-tagged-comments-message item)))
    (println "\nCreating PR comments...")
    (doseq [item files-with-tagged-comments]
      (println (str "File: " (:filename item)))
      (doseq [comment (:tagged-comments item)]
        (let [response (create-gh-pr-comment (:filename item) comment pr-data)]
          (println "Created comment response:")
          (pprint/pprint
           (-> response
               :out
               (json/read-str :key-fn keyword))))))))
