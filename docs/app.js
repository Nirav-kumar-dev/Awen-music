/**
 * Awen Official Showcase & Real-Time GitHub Downloads Tracker
 * Repository: Nirav-kumar-dev/Awen-music
 */

const REPO_OWNER = 'Nirav-kumar-dev';
const REPO_NAME = 'Awen-music';
const GITHUB_API_BASE = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}`;

// Interactive Mood Demo Data
const MOOD_PREVIEWS = {
    "Late Night Drive": {
        title: "Midnight Highway Reverie",
        vibe: "Atmospheric synthwave and dreamy nocturnal bass tailored to your late-night journey.",
        tracks: [
            { num: 1, title: "Midnight City", artist: "M83" },
            { num: 2, title: "Nightcall", artist: "Kavinsky" },
            { num: 3, title: "Electric Feel", artist: "MGMT" },
            { num: 4, title: "Resonance", artist: "HOME" }
        ]
    },
    "Deep Focus & Code": {
        title: "Algorithmic Flow State",
        vibe: "Intricate downtempo and ambient textures designed for deep focus and flow.",
        tracks: [
            { num: 1, title: "Awake", artist: "Tycho" },
            { num: 2, title: "Dayvan Cowboy", artist: "Boards of Canada" },
            { num: 3, title: "Cirrus", artist: "Bonobo" },
            { num: 4, title: "A Walk", artist: "Tycho" }
        ]
    },
    "Workout Beast Mode": {
        title: "High-Octane Adrenaline",
        vibe: "Aggressive basslines and relentless bpm for setting personal records.",
        tracks: [
            { num: 1, title: "Till I Collapse", artist: "Eminem" },
            { num: 2, title: "Bangarang", artist: "Skrillex" },
            { num: 3, title: "Breathe", artist: "The Prodigy" },
            { num: 4, title: "Can't Be Touched", artist: "Roy Jones Jr." }
        ]
    },
    "Rainy Day Melancholy": {
        title: "Window Pane Acoustics",
        vibe: "Delicate acoustic melodies and poignant vocals for cozy reflection.",
        tracks: [
            { num: 1, title: "Motion Sickness", artist: "Phoebe Bridgers" },
            { num: 2, title: "Apocalypse", artist: "Cigarettes After Sex" },
            { num: 3, title: "Fake Plastic Trees", artist: "Radiohead" },
            { num: 4, title: "Skinny Love", artist: "Bon Iver" }
        ]
    },
    "Cyberpunk Synthwave": {
        title: "Neon Dystopia 2077",
        vibe: "Dark electro arpeggiators and analogue drums echoing through rainy mega-cities.",
        tracks: [
            { num: 1, title: "Turbo Killer", artist: "Carpenter Brut" },
            { num: 2, title: "Future Club", artist: "Perturbator" },
            { num: 3, title: "Fly For Your Life", artist: "GUNSHIP" },
            { num: 4, title: "Tech Noir", artist: "GUNSHIP" }
        ]
    }
};

document.addEventListener('DOMContentLoaded', () => {
    initGitHubRealtimeTracker();
    initInteractiveAIDemo();
});

/**
 * Real-time GitHub Downloads & Stats Tracker
 */
async function initGitHubRealtimeTracker() {
    const downloadsElem = document.getElementById('total-downloads-count');
    const versionElem = document.getElementById('latest-version-name');
    const starsElem = document.getElementById('github-stars-count');
    const navStarsElem = document.getElementById('nav-stars-count');
    const navVersionElem = document.getElementById('nav-version');
    const refreshBtn = document.getElementById('refresh-stats-btn');
    const directApkInfo = document.getElementById('direct-apk-info');
    const heroMeta = document.getElementById('hero-apk-meta');
    const qrImg = document.getElementById('qr-code-img');

    const downloadButtons = [
        document.getElementById('hero-download-button'),
        document.getElementById('qr-download-button'),
        document.getElementById('nav-download-cta')
    ];

    async function fetchStats() {
        if (refreshBtn) refreshBtn.classList.add('spinning');

        try {
            // 1. Fetch Repository Metadata (Stars, Forks)
            const repoRes = await fetch(GITHUB_API_BASE);
            if (repoRes.ok) {
                const repoData = await repoRes.json();
                const stars = repoData.stargazers_count || 0;
                if (starsElem) starsElem.textContent = `★ ${stars.toLocaleString()}`;
                if (navStarsElem) navStarsElem.textContent = `★ ${stars.toLocaleString()}`;
            }

            // 2. Fetch Releases & Calculate Total Real-Time Downloads
            const releasesRes = await fetch(`${GITHUB_API_BASE}/releases`);
            if (releasesRes.ok) {
                const releases = await releasesRes.json();
                
                let totalDownloads = 0;
                let latestRelease = null;
                let latestApkAsset = null;

                if (Array.isArray(releases) && releases.length > 0) {
                    latestRelease = releases[0];

                    releases.forEach(rel => {
                        if (rel.assets && Array.isArray(rel.assets)) {
                            rel.assets.forEach(asset => {
                                totalDownloads += (asset.download_count || 0);
                                if (!latestApkAsset && asset.name && asset.name.endsWith('.apk')) {
                                    latestApkAsset = asset;
                                }
                            });
                        }
                    });

                    // Update Version Badges
                    const tagName = latestRelease.tag_name || 'v1.0.0';
                    if (versionElem) versionElem.textContent = tagName;
                    if (navVersionElem) navVersionElem.textContent = tagName;

                    // Update Download Buttons & Direct APK Links
                    const targetDownloadUrl = latestApkAsset 
                        ? latestApkAsset.browser_download_url 
                        : (latestRelease.html_url || `https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/latest`);

                    downloadButtons.forEach(btn => {
                        if (btn) btn.href = targetDownloadUrl;
                    });

                    // Update Asset Size & Labels
                    const apkSizeMB = latestApkAsset 
                        ? (latestApkAsset.size / (1024 * 1024)).toFixed(1) + ' MB' 
                        : '~69 MB';

                    if (heroMeta) {
                        heroMeta.textContent = `${tagName} • ${apkSizeMB} • Direct from GitHub`;
                    }
                    if (directApkInfo) {
                        directApkInfo.textContent = `Latest: ${tagName} (${apkSizeMB}) • Direct Release Asset`;
                    }

                    // Update QR Code to direct link
                    if (qrImg) {
                        qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(targetDownloadUrl)}&color=ffffff&bgcolor=141724`;
                    }

                    // Animate Download Count
                    animateCounter(downloadsElem, totalDownloads);
                } else {
                    // No releases published yet on GitHub - display ready state
                    if (downloadsElem) downloadsElem.textContent = 'Active (Ready)';
                    if (versionElem) versionElem.textContent = 'v1.0.0';
                    if (directApkInfo) directApkInfo.textContent = 'Ready for initial release v1.0.0';
                }
            } else {
                // If rate limited or unavailable, provide graceful fallback
                if (downloadsElem) downloadsElem.textContent = 'Live Tracking';
            }
        } catch (error) {
            console.warn('Live GitHub tracker notice:', error);
            if (downloadsElem) downloadsElem.textContent = 'Active';
        } finally {
            if (refreshBtn) {
                setTimeout(() => refreshBtn.classList.remove('spinning'), 500);
            }
        }
    }

    // Initial fetch
    fetchStats();

    // Auto-refresh every 60 seconds
    setInterval(fetchStats, 60000);

    // Manual refresh click
    if (refreshBtn) {
        refreshBtn.addEventListener('click', fetchStats);
    }
}

/**
 * Animated Number Counter
 */
function animateCounter(element, target) {
    if (!element) return;
    const start = 0;
    const duration = 1200;
    const startTime = performance.now();

    function update(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const easeOut = 1 - Math.pow(1 - progress, 3);
        const current = Math.floor(start + (target - start) * easeOut);
        
        element.textContent = current.toLocaleString();

        if (progress < 1) {
            requestAnimationFrame(update);
        } else {
            element.textContent = target.toLocaleString();
        }
    }

    requestAnimationFrame(update);
}

/**
 * Interactive Nemotron AI Simulator Demo
 */
function initInteractiveAIDemo() {
    const input = document.getElementById('ai-demo-input');
    const btn = document.getElementById('ai-demo-btn');
    const pills = document.querySelectorAll('.mood-pill');
    const titleElem = document.getElementById('preview-playlist-title');
    const vibeElem = document.getElementById('preview-playlist-vibe');
    const tracklistElem = document.getElementById('preview-tracklist');

    function renderPreview(moodName) {
        const data = MOOD_PREVIEWS[moodName] || {
            title: `${moodName.charAt(0).toUpperCase() + moodName.slice(1)} Curation`,
            vibe: `Custom Nemotron 3.5 audio curation tuned to "${moodName}" with dynamic bass and melodic transitions.`,
            tracks: [
                { num: 1, title: `${moodName} Theme`, artist: "Awen AI Sound" },
                { num: 2, title: "Acoustic Resonance", artist: "Lo-Fi Collective" },
                { num: 3, title: "Harmonic Pulse", artist: "Ambient Wave" },
                { num: 4, title: "Drift Away", artist: "Nocturnal Studio" }
            ]
        };

        if (titleElem) titleElem.textContent = data.title;
        if (vibeElem) vibeElem.textContent = data.vibe;

        if (tracklistElem) {
            tracklistElem.innerHTML = data.tracks.map(t => `
                <div class="track-row">
                    <span class="track-num">${t.num}</span>
                    <div class="track-info">
                        <span class="track-title">${t.title}</span>
                        <span class="track-artist">${t.artist}</span>
                    </div>
                    <span class="track-status">Playable</span>
                </div>
            `).join('');
        }
    }

    pills.forEach(pill => {
        pill.addEventListener('click', () => {
            pills.forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            const mood = pill.getAttribute('data-mood');
            if (input) input.value = mood;
            renderPreview(mood);
        });
    });

    if (btn) {
        btn.addEventListener('click', () => {
            const val = input ? input.value.trim() : '';
            if (val) {
                renderPreview(val);
            }
        });
    }

    if (input) {
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const val = input.value.trim();
                if (val) renderPreview(val);
            }
        });
    }
}
