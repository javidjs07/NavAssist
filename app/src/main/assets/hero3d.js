/* NavAssist 3D hero scenes: ?scene=orb | tiles | rings  &shift=1 (move artwork right to make room for text) */
(function () {
  'use strict';
  var params = new URLSearchParams(location.search);
  var sceneName = params.get('scene') || 'orb';
  var shift = parseFloat(params.get('shift') || '1');

  var renderer;
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
  } catch (e) { return; }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setClearColor(0x000000, 0);
  renderer.outputEncoding = THREE.sRGBEncoding;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.0;
  document.body.appendChild(renderer.domElement);

  var scene = new THREE.Scene();
  var camera = new THREE.PerspectiveCamera(36, 1, 0.1, 100);
  camera.position.set(0, 0, 10);

  // ---- lighting + a procedural studio environment so glossy materials have something to reflect
  scene.add(new THREE.HemisphereLight(0xaab8ff, 0x0b0f19, 0.35));
  var key = new THREE.DirectionalLight(0xffffff, 0.9);
  key.position.set(-4, 5, 6);
  scene.add(key);
  var rimLight = new THREE.PointLight(0x38bdf8, 1.3, 40);
  rimLight.position.set(6, -2, 4);
  scene.add(rimLight);
  var fillLight = new THREE.PointLight(0x6366f1, 1.3, 40);
  fillLight.position.set(-6, -3, 3);
  scene.add(fillLight);

  try {
    var pm = new THREE.PMREMGenerator(renderer);
    var env = new THREE.Scene();
    var dome = new THREE.Mesh(
      new THREE.SphereGeometry(20, 32, 16),
      new THREE.ShaderMaterial({
        side: THREE.BackSide,
        vertexShader: 'varying vec3 vP; void main(){ vP = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
        fragmentShader: 'varying vec3 vP; void main(){ float h = vP.y * 0.5 + 0.5; vec3 c = mix(vec3(0.03,0.04,0.10), vec3(0.50,0.58,1.0), pow(h, 1.5)); gl_FragColor = vec4(c, 1.0); }'
      })
    );
    env.add(dome);
    var addPanel = function (w, h, x, y, z, color, power) {
      var m = new THREE.MeshBasicMaterial({ color: color, side: THREE.DoubleSide });
      m.color.multiplyScalar(power);
      var p = new THREE.Mesh(new THREE.PlaneGeometry(w, h), m);
      p.position.set(x, y, z);
      p.lookAt(0, 0, 0);
      env.add(p);
    };
    addPanel(8, 4, -8, 8, 6, 0xffffff, 6);
    addPanel(5, 5, 9, -2, 5, 0x38bdf8, 5);
    addPanel(6, 3, -9, -4, 3, 0x6366f1, 4);
    addPanel(10, 2, 0, 10, -4, 0xffffff, 3);
    scene.environment = pm.fromScene(env, 0.02).texture;
    pm.dispose();
  } catch (e) { /* devices without float textures just get the plain lights */ }

  var silver = new THREE.MeshPhysicalMaterial({ color: 0xdfe4ff, metalness: 0.85, roughness: 0.2, clearcoat: 1, clearcoatRoughness: 0.08, envMapIntensity: 1.3 });
  var cyan = new THREE.MeshPhysicalMaterial({ color: 0x1db8f0, metalness: 0.1, roughness: 0.12, clearcoat: 1, clearcoatRoughness: 0.05, envMapIntensity: 1.2 });
  var indigo = new THREE.MeshPhysicalMaterial({ color: 0x6366f1, metalness: 0.2, roughness: 0.18, clearcoat: 1, clearcoatRoughness: 0.06, envMapIntensity: 1.2 });
  var tileMat = new THREE.MeshPhysicalMaterial({ color: 0x2b3566, metalness: 0.35, roughness: 0.3, clearcoat: 0.8, clearcoatRoughness: 0.15, envMapIntensity: 1.0 });
  var glyphMat = new THREE.MeshBasicMaterial({ color: 0xf9fafb });

  function roundedRect(w, h, r) {
    var s = new THREE.Shape();
    var x = -w / 2, y = -h / 2;
    s.moveTo(x + r, y);
    s.lineTo(x + w - r, y);
    s.quadraticCurveTo(x + w, y, x + w, y + r);
    s.lineTo(x + w, y + h - r);
    s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    s.lineTo(x + r, y + h);
    s.quadraticCurveTo(x, y + h, x, y + h - r);
    s.lineTo(x, y + r);
    s.quadraticCurveTo(x, y, x + r, y);
    return s;
  }

  function tileGeometry(size) {
    var g = new THREE.ExtrudeGeometry(roundedRect(size, size, size * 0.28), {
      depth: size * 0.16, bevelEnabled: true, bevelThickness: size * 0.06,
      bevelSize: size * 0.06, bevelSegments: 5, curveSegments: 14
    });
    g.center();
    return g;
  }

  function glyph(kind, size) {
    var u = size * 0.2, lw = size * 0.07, shape, hole, geo;
    if (kind === 'home') {
      geo = new THREE.RingGeometry(u * 1.05 - lw, u * 1.05, 40);
    } else if (kind === 'back') {
      shape = new THREE.Shape();
      shape.moveTo(u * 0.9, u * 1.15); shape.lineTo(u * 0.9, -u * 1.15); shape.lineTo(-u * 1.15, 0); shape.lineTo(u * 0.9, u * 1.15);
      hole = new THREE.Path();
      var k = 0.6;
      hole.moveTo(u * 0.9 * k + u * 0.12, u * 1.15 * k); hole.lineTo(-u * 1.15 * k + u * 0.12, 0); hole.lineTo(u * 0.9 * k + u * 0.12, -u * 1.15 * k); hole.lineTo(u * 0.9 * k + u * 0.12, u * 1.15 * k);
      shape.holes.push(hole);
      geo = new THREE.ShapeGeometry(shape);
    } else if (kind === 'recents') {
      shape = roundedRect(u * 2, u * 2, u * 0.3);
      hole = new THREE.Path();
      var q = u - lw;
      hole.moveTo(-q, -q); hole.lineTo(-q, q); hole.lineTo(q, q); hole.lineTo(q, -q); hole.lineTo(-q, -q);
      shape.holes.push(hole);
      geo = new THREE.ShapeGeometry(shape);
    } else if (kind === 'dot') {
      geo = new THREE.CircleGeometry(u * 0.5, 32);
    } else {
      return null;
    }
    var m = new THREE.Mesh(geo, glyphMat);
    m.position.z = size * 0.16 / 2 + size * 0.06 + 0.005;
    return m;
  }

  function makeTile(size, kind, material) {
    var t = new THREE.Group();
    t.add(new THREE.Mesh(tileGeometry(size), material || tileMat));
    var g = glyph(kind, size);
    if (g) t.add(g);
    return t;
  }

  var root = new THREE.Group();
  scene.add(root);
  var extentX = 4, extentY = 6.4;
  var animators = [];

  if (sceneName === 'tiles') {
    extentX = 4.4; extentY = 4.4;
    var kinds = ['back', 'home', 'recents', 'dot', null, 'dot', 'recents', 'home', 'back'];
    for (var i = 0; i < 9; i++) {
      var col = i % 3, row = Math.floor(i / 3);
      var tile = makeTile(1.15, kinds[i], i === 4 ? indigo : tileMat);
      tile.position.set((col - 1) * 1.45, (1 - row) * 1.45, 0);
      root.add(tile);
      (function (tl, idx) {
        animators.push(function (t) {
          tl.position.z = Math.sin(t * 1.3 + idx * 0.7) * 0.28;
          tl.rotation.x = Math.sin(t * 0.9 + idx) * 0.12;
          tl.rotation.y = Math.cos(t * 0.8 + idx * 0.5) * 0.14;
        });
      })(tile, i);
    }
  } else if (sceneName === 'rings') {
    extentX = 5.2; extentY = 5.2;
    var ringDefs = [[0.95, 0.12, silver], [1.45, 0.10, indigo], [1.95, 0.09, silver], [2.45, 0.08, cyan]];
    ringDefs.forEach(function (d, i) {
      var r = new THREE.Mesh(new THREE.TorusGeometry(d[0], d[1], 32, 128), d[2]);
      root.add(r);
      animators.push(function (t) {
        r.rotation.x = t * (0.25 + i * 0.07) * (i % 2 ? -1 : 1) + i;
        r.rotation.y = t * (0.18 + i * 0.05) + i * 0.6;
      });
    });
    var core = new THREE.Mesh(new THREE.SphereGeometry(0.5, 64, 48), cyan);
    root.add(core);
    animators.push(function (t) { core.scale.setScalar(1 + Math.sin(t * 2) * 0.05); });
  } else {
    // 'orb' : ring + glossy ball + the three navigation keys
    var torus = new THREE.Mesh(new THREE.TorusGeometry(1.5, 0.5, 48, 128), silver);
    root.add(torus);
    var ball = new THREE.Mesh(new THREE.SphereGeometry(0.62, 64, 48), cyan);
    root.add(ball);
    var kx = [-1.55, 0, 1.55], kk = ['back', 'home', 'recents'];
    var keys = [];
    for (var j = 0; j < 3; j++) {
      var k2 = makeTile(1.1, kk[j]);
      k2.position.set(kx[j], -2.85, 0);
      root.add(k2);
      keys.push(k2);
    }
    var moons = [];
    [[0.16, indigo, 2.35], [0.11, cyan, 2.7], [0.13, silver, 3.0]].forEach(function (m, i) {
      var s = new THREE.Mesh(new THREE.SphereGeometry(m[0], 24, 16), m[1]);
      root.add(s);
      moons.push([s, m[2], i]);
    });
    animators.push(function (t) {
      torus.rotation.z = t * 0.3;
      ball.position.y = Math.sin(t * 1.4) * 0.07;
      keys.forEach(function (kk2, i) { kk2.position.y = -2.85 + Math.sin(t * 1.6 + i * 1.1) * 0.08; kk2.rotation.y = Math.sin(t * 0.8 + i) * 0.18; });
      moons.forEach(function (m) {
        var a = t * (0.6 - m[2] * 0.12) + m[2] * 2.1;
        m[0].position.set(Math.cos(a) * m[1] * 0.75, Math.sin(a) * m[1] * 0.55, Math.sin(a * 1.3) * 0.6);
      });
    });
  }

  // twinkling dust
  var N = 140, pos = new Float32Array(N * 3);
  for (var n = 0; n < N; n++) { pos[n * 3] = (Math.random() - 0.5) * 16; pos[n * 3 + 1] = (Math.random() - 0.5) * 9; pos[n * 3 + 2] = -2 - Math.random() * 5; }
  var dustGeo = new THREE.BufferGeometry();
  dustGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  var dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({ color: 0xffffff, size: 0.05, transparent: true, opacity: 0.7, depthWrite: false }));
  scene.add(dust);

  // drag to rotate (horizontal drags), eases back when released
  var pressed = false, lastX = 0, lastY = 0, dragY = 0, dragX = 0, tY = 0, tX = 0;
  var el = renderer.domElement;
  el.addEventListener('pointerdown', function (e) { pressed = true; lastX = e.clientX; lastY = e.clientY; });
  window.addEventListener('pointerup', function () { pressed = false; });
  window.addEventListener('pointercancel', function () { pressed = false; });
  window.addEventListener('pointermove', function (e) {
    if (!pressed) return;
    tY += (e.clientX - lastX) * 0.012;
    tX += (e.clientY - lastY) * 0.008;
    tY = Math.max(-1.3, Math.min(1.3, tY));
    tX = Math.max(-0.5, Math.min(0.5, tX));
    lastX = e.clientX; lastY = e.clientY;
  });

  function resize() {
    var w = Math.max(1, window.innerWidth), h = Math.max(1, window.innerHeight);
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    var vh = 2 * Math.tan(camera.fov * Math.PI / 360) * camera.position.z;
    var vw = vh * camera.aspect;
    var s = Math.min(vh * 0.9 / extentY, vw * (shift > 0 ? 0.5 : 0.9) / extentX);
    root.scale.setScalar(s);
    root.position.x = vw * 0.23 * shift;
  }
  window.addEventListener('resize', resize);
  resize();

  var start = Date.now();
  function frame() {
    var t = (Date.now() - start) / 1000;
    if (!pressed) { tY *= 0.94; tX *= 0.94; }
    dragY += (tY - dragY) * 0.15;
    dragX += (tX - dragX) * 0.15;
    root.rotation.y = Math.sin(t * 0.6) * 0.4 + dragY;
    root.rotation.x = Math.sin(t * 0.45) * 0.1 + dragX;
    root.position.y = Math.sin(t * 0.9) * 0.08;
    for (var a = 0; a < animators.length; a++) animators[a](t);
    dust.rotation.y = t * 0.02;
    renderer.render(scene, camera);
  }
  renderer.setAnimationLoop(frame);
})();
