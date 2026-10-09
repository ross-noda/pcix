const paths: Record<string, string> = {
  tasks:
    "M3 6 Q3 3 6 3H18Q21 3 21 6V18Q21 21 18 21H6Q3 21 3 18ZM7 12 10 15 17 8",
  calendar:
    "M6 5H18Q21 5 21 8V18Q21 21 18 21H6Q3 21 3 18V8Q3 5 6 5M3 10H21M7 3V7M17 3V7M8 14h.01M15 14h.01",
  matrix: "M3 3H10V10H3ZM14 3H21V10H14ZM3 14H10V21H3ZM14 14H21V21H14Z",
  settings:
    "M12 2 21 7V17L12 22 3 17V7ZM15.5 12a3.5 3.5 0 1 0-7 0 3.5 3.5 0 0 0 7 0",
  search: "M17 10a7 7 0 1 0-14 0 7 7 0 0 0 14 0M15 15 21 21",
  plus: "M12 4V20M4 12H20",
  close: "M6 6 18 18M18 6 6 18",
  back: "M13 4 5 12 13 20M5 12H21",
  tag: "M3 3H12L22 13 13 22 3 12ZM9 7.5a1.5 1.5 0 1 0-3 0 1.5 1.5 0 0 0 3 0",
  inbox: "M3 4H21V21H3ZM3 13H8L9 16H15L16 13H21",
  habits: "M3 10V6H20L16 2M21 14V18H4L8 22",
  more: "M12 5h.01M12 12h.01M12 19h.01",
  delete: "M3 6H21M8 6V3H16V6M5 6 6 21H18L19 6M10 10V17M14 10V17",
  clock: "M21 12a9 9 0 1 0-18 0 9 9 0 0 0 18 0M12 6V12L16 14",
  children: "M3 4H8V9H3ZM3 15H8V20H3ZM12 6H21M12 17H21",
  check: "M5 12 10 17 20 6",
  image:
    "M3 3H21V21H3ZM10 8a2 2 0 1 0-4 0 2 2 0 0 0 4 0M3 18 9 12 13 16 17 11 21 15",
};
export function Icon({ name }: { name: string }) {
  return (
    <svg
      className="icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={name === "more" ? 3 : 1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] ?? paths.matrix} />
    </svg>
  );
}
