# bcviz — Java Bytecode Visualizer

A tool that reads compiled `.class` files and shows exactly what the JVM does,
instruction by instruction: decoded opcodes, human-readable operands, and a
live symbolic operand stack you can step through in a browser.

Built on top of [ASM](https://asm.ow2.io/) (the same bytecode library used
inside the JDK, Spring, Kotlin, and Gradle) for parsing, with all the
disassembly, opcode explanation, and stack simulation written from scratch.

---

## 1. Why this project exists

Most portfolio projects are CRUD apps. This one is JVM tooling — it proves
you understand what "compiling to bytecode" actually means, how the operand
stack works, and how a method call really moves data around at runtime.
That's a rare, deep-systems angle that stands out in interviews, and it's
the kind of thing you can actually explain confidently because you built
every layer of it yourself.

---

## 2. What it actually does — the mental model

When you compile `MyFile.java`, `javac` doesn't produce machine code. It
produces **bytecode**: a compact set of instructions for a virtual machine
(the JVM) to interpret. Each instruction does one small, precise thing —
push a value, pop two values and add them, jump to another instruction,
call a method — and the JVM executes these one at a time using a data
structure called the **operand stack**.

This tool:
1. Reads that raw bytecode out of your `.class` file
2. Translates each instruction into something readable
3. Simulates what happens to the operand stack as each instruction runs
4. Shows you that trace, live, in a browser, instruction by instruction

So instead of just reading Java source and imagining what happens, you're
watching the actual low-level execution model your code compiles down to.

---

## 3. Quick start (Windows, matches what you already have working)

You already confirmed this works. For reference / reinstalling on another
machine:

```powershell
# 1. unzip and cd into the project
cd path\to\bytecode-visualizer

# 2. compile the project's own source code
javac -cp "lib\asm-9.7.jar;lib\asm-tree-9.7.jar;lib\asm-util-9.7.jar;lib\asm-analysis-9.7.jar" -d out src\com\prasad\bcviz\*.java

# 3. compile whatever sample class you want to inspect
#    --release 21 matters (see Troubleshooting below)
javac -d samples --release 21 samples\Sample.java

# 4. start the server
java -cp "out;lib\asm-9.7.jar;lib\asm-tree-9.7.jar;lib\asm-util-9.7.jar;lib\asm-analysis-9.7.jar" com.prasad.bcviz.WebServer samples web 8080
```

Then open **http://localhost:8080** in a browser. Keep the terminal window
open — closing it stops the server.

### Adding your own code
1. Write or drop in any `.java` file
2. `javac -d samples --release 21 YourFile.java`
3. Refresh the browser tab — no need to restart the server, it re-reads the
   `samples` folder on every request
4. Click your class in the sidebar → click a method → step through with
   **next / prev**

---

## 4. Architecture — how the pieces fit together

```
.class file
    │
    ▼
ClassInfoExtractor   — parses class metadata (name, super, fields, methods)
    │                   via ASM's ClassReader + ClassNode (tree API)
    ▼
Disassembler         — walks each method's instruction list, decodes every
    │                   instruction's opcode + operand into readable form
    ▼
OpcodeInfo            — static table: description + stack effect (pop/push)
StackEffectCalculator — resolves VARIABLE stack effects (method calls, field
                         access, ldc) from the instruction's actual descriptor
    │
    ▼
StackSimulator        — single-pass symbolic execution: maintains a real
    │                    stack of readable expressions ("this", "(a + b)",
    │                    "this.add(2, 3)") as it walks the method
    ▼
Analyzer + Json        — combines everything into one JSON document per class
    │
    ▼
WebServer               — JDK's built-in HttpServer, serves the API + the
    │                     static frontend (index.html / style.css / app.js)
    ▼
Browser UI               — instruction table + interactive stack panel,
                            color-coded by opcode category, step-through
                            controls
```

### Design decisions worth understanding (interview-ready explanations)

**Why the tree API (`ClassNode`) instead of the streaming visitor API?**
A tree gives random access to the whole method body at once. The
label-resolution pass (turning jump targets into readable `-> L2` instead of
raw object references) and the stack simulator both need to look ahead/behind
in the instruction list, which a one-pass streaming visitor can't do easily.

**Why is the stack simulator "single-pass" rather than full data-flow
analysis?**
A method with branches technically has multiple possible stack states
depending on which path executes. Modeling that properly (like the JVM's own
bytecode verifier does) means tracking a *set* of possible states per program
point and merging them at labels — a significantly bigger project. This
simulator instead does one honest linear pass through the instruction list in
source order. That's exactly correct for straight-line code, and still
produces an accurate, readable trace for loop/branch bodies, since they're
linear internally — the simulator just doesn't fork into "if taken / if not
taken" as two separate simulated paths. This is a deliberate, documented
scope decision (see the class-level comment in `StackSimulator.java`), not an
oversight — a good thing to say explicitly if asked about it.

**Why does `StackEffectCalculator` exist separately from `OpcodeInfo`?**
Most opcodes (like `iadd`) have a fixed stack effect: always pops 2, pushes 1.
But `invokevirtual` on `int add(int,int)` pops 3 values (object + 2 args)
while `invokevirtual` on `void log(String)` pops 2 — same opcode, different
effect, because it depends on the method descriptor attached to that specific
instruction. `OpcodeInfo` holds the fixed table for the ~150 opcodes with a
constant effect; `StackEffectCalculator` computes the handful of variable
cases from the real descriptor using ASM's `Type` class.

**Why a hand-rolled JSON writer instead of a library?**
The sandbox this was built in couldn't reach Maven Central, only a curated
allowlist of domains (npm, PyPI, GitHub, Ubuntu's apt mirrors). ASM was
available via `apt` (`libasm-java`), but a JSON library wasn't, so `Json.java`
is a ~40-line serializer that's good enough for this project's needs. In your
own dev environment with full internet access, you'd normally just add
`org.json` or `Gson` as a dependency instead — see Section 6.

---

## 5. Project layout

```
bytecode-visualizer/
├── lib/                     ASM jars (asm, asm-tree, asm-util, asm-analysis)
├── samples/                 .java/.class files you're inspecting
├── src/com/prasad/bcviz/
│   ├── ClassInfoExtractor.java
│   ├── Disassembler.java
│   ├── OpcodeInfo.java
│   ├── StackEffectCalculator.java
│   ├── StackSimulator.java
│   ├── Analyzer.java
│   ├── Json.java
│   └── WebServer.java
├── web/                     frontend: index.html, style.css, app.js
├── build.sh / run.sh        Mac/Linux convenience scripts
└── README.md
```

---

## 6. Troubleshooting

**`{"error":"Unsupported class file major version 68"}` (or similar number)**
Your `javac` compiled to a newer bytecode version than the bundled ASM 9.7
library recognizes. Major version 68 = Java 24; ASM 9.7 reliably handles up
to major version 65 (Java 21). Fix: always compile your sample files with
`--release 21`:
```
javac -d samples --release 21 YourFile.java
```
This only affects files you compile *for the tool to inspect* — it doesn't
change your actual JDK installation or affect any other project.

**Class doesn't show up in the sidebar after compiling**
Confirm the `.class` file actually landed in the `samples` folder the server
was started with (check the "Serving .class files from: ..." line printed
when the server starts). If you compiled into a different folder, either
recompile with `-d samples` or restart the server pointed at the right folder:
```
java -cp "out;lib\...jars..." com.prasad.bcviz.WebServer <yourFolder> web 8080
```

**Port 8080 already in use**
Pick a different port as the last argument:
```
java -cp "out;lib\...jars..." com.prasad.bcviz.WebServer samples web 8090
```
then open `http://localhost:8090`.

**Blank white/black page, nothing loads**
Open DevTools (F12) → Console tab, and check for errors. Most issues surface
there with a specific message — share that and it's usually a quick fix.

---

## 7. Deployment — putting this somewhere other than localhost

Important context first: **this tool reads server-side files** (`.class`
files sitting in a folder on whatever machine runs it). That makes it
fundamentally a *local developer tool*, not a public web app — you wouldn't
want strangers on the internet uploading arbitrary `.class` files to your
server and having your JVM parse them (that's a real security surface, not
just a technicality). So "deploying" this sensibly means one of a few things
depending on what you're actually trying to achieve:

### Option A — Just for your own use / demo (recommended)
Keep running it locally exactly as you are now. This is genuinely the right
call for a JVM introspection tool. For a resume/interview demo, a short
screen recording or a live localhost demo during the interview both work
great — you don't need it hosted publicly for it to be a legitimate,
impressive project.

### Option B — Temporary public link for sharing (e.g. showing a friend/recruiter)
Use a tunneling tool to expose your local server temporarily without
deploying anywhere:
```
ngrok http 8080
```
(Install ngrok, run that command while your server is running.) It gives you
a temporary public URL like `https://random-id.ngrok-free.app` that forwards
to your local machine. Free tier is fine for demos; the link expires when you
stop it.

### Option C — Real hosting on a small cloud VM (for a proper portfolio deploy)
If you want a permanent public URL, the cleanest approach is:
1. Get a small VM (AWS EC2 free tier, Oracle Cloud free tier, or a cheap
   DigitalOcean/Linode droplet all work — pick whichever you're already
   comfortable with)
2. Install a JDK on it (`sudo apt install openjdk-21-jdk`)
3. Copy the project folder over (`scp` or `git clone` if you push it to
   GitHub first — recommended anyway for your portfolio)
4. Run the server, ideally as a background service so it survives reboots/
   disconnects — `systemd` is the standard way:
   ```ini
   # /etc/systemd/system/bcviz.service
   [Unit]
   Description=bcviz bytecode visualizer
   After=network.target

   [Service]
   WorkingDirectory=/home/youruser/bytecode-visualizer
   ExecStart=/usr/bin/java -cp "out:lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-util-9.7.jar:lib/asm-analysis-9.7.jar" com.prasad.bcviz.WebServer samples web 8080
   Restart=always
   User=youruser

   [Install]
   WantedBy=multi-user.target
   ```
   Then: `sudo systemctl enable --now bcviz`
5. Open port 8080 in the VM's firewall/security group, or put a reverse
   proxy (nginx/Caddy) in front on port 80/443 with a real domain — Caddy is
   the easiest if you've never set one up, it does HTTPS automatically.
6. **Before making it public**, restrict what it can do: don't let it accept
   file *uploads* from visitors (this version only reads local `samples/`,
   which is safe), and treat it as read-only/demo-only rather than a
   production service.

### Option D — Push it to GitHub (do this regardless of A/B/C)
This matters more than actual hosting for a portfolio project — recruiters
and interviewers look at the repo, not a live URL, most of the time:
```
cd bytecode-visualizer
git init
git add .
git commit -m "Java bytecode visualizer built on ASM"
```
Then create a repo on GitHub and push. Add a couple of screenshots to the
README (like the ones from your working session) — a visual README is worth
a lot more than a wall of text for a project like this.

---

## 8. Natural next steps if you want to keep building

- **Control flow graph** — render each method as a graph of basic blocks
  using the branch/jump targets already decoded by the `Disassembler`. All
  the underlying data for this already exists; it's a rendering exercise.
- **Full data-flow stack simulation** — track multiple possible stack states
  per branch and merge at labels, closer to what the JVM verifier itself does.
- **.jar support** — currently reads a directory of loose `.class` files;
  extending `WebServer`'s file listing to unzip a `.jar` is a small change.
- **File upload in the UI** — let you drag-and-drop a `.class` file instead
  of manually compiling into `samples/` (worth doing *before* any public
  deployment, with real validation, given the security note in Section 7).
- **Migrate to Maven/Gradle** — this build uses jars installed via `apt`
  for a dependency-free sandbox. In a normal dev environment, pull ASM from
  Maven Central instead:
  ```xml
  <dependency>
    <groupId>org.ow2.asm</groupId>
    <artifactId>asm-tree</artifactId>
    <version>9.7</version>
  </dependency>
  ```
