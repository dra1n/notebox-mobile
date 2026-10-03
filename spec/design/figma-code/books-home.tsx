// Figma get_design_context output for node 1502:435 ("notebox-books-home"), saved 2026-10-03.
// React + Tailwind REFERENCE ONLY: read it for exact values and translate them to RN StyleSheet +
// notebox.ui.theme tokens. Asset URLs are replaced with the local copies in ../assets. The four
// identical book rows are collapsed to one. The status bar is the device's.

const imgHumburger = "../assets/hamburger.svg";
const imgSearch = "../assets/search.svg";
const imgBook = "../assets/book.svg";

function BtnAddNote({ className }: { className?: string }) {
  return (
    <div className={className || "h-[28px] relative w-[99px]"} data-node-id="0:1534" data-name="btn add note">
      <div className="absolute bg-[#ade4ea] inset-0 rounded-[3px]" data-node-id="309:311" />
      <p className="absolute font-['Roboto:Medium'] font-medium left-[15.15%] right-[13.13%] text-[#323232] text-[14px] top-[calc(50%-8px)] tracking-[0.6px] whitespace-nowrap" data-node-id="309:312">
        ADD NOTE
      </p>
    </div>
  );
}

export default function NoteboxBooksHome() {
  return (
    <div className="bg-[#f6f6f6] flex flex-col items-start overflow-clip relative size-full" data-node-id="1502:435">
      <div className="flex flex-[1_0_0] flex-col items-start min-h-px relative w-full" data-node-id="1502:442">
        <div className="bg-[#2c292b] flex h-[64px] items-center justify-between px-[16px] relative shrink-0 w-full" data-node-id="1502:443" data-name="header">
          <div className="flex gap-[8px] items-center relative shrink-0" data-node-id="1502:444" data-name="left-trigger">
            <div className="h-[19px] opacity-63 relative shrink-0 w-[23px]" data-node-id="1506:454" data-name="humburger">
              <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgHumburger} />
            </div>
            {/* wordmark: Amatic SC Bold 28; "Note" #6cc5cf, "Box" #c6c6c6 — ship as an image asset */}
            <p className="font-['Amatic_SC:Bold'] text-[#6cc5cf] text-[28px] whitespace-nowrap" data-node-id="1502:450">
              <span>Note</span>
              <span className="text-[#c6c6c6]">Box</span>
            </p>
          </div>
          <BtnAddNote className="h-[28px] relative shrink-0 w-[99px]" />
        </div>
        <div className="bg-white border-[#dfdfdf] border-b flex flex-col gap-[12px] items-start p-[16px] relative shrink-0 w-full" data-node-id="1502:454" data-name="search-and-stats">
          <div className="bg-[#f6f6f6] flex gap-[8px] items-center px-[12px] py-[10px] rounded-[8px] w-full" data-node-id="1502:455" data-name="search-input">
            <div className="relative shrink-0 size-[16px]" data-node-id="1502:727" data-name="search">
              <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgSearch} />
            </div>
            <p className="flex-[1_0_0] font-['Roboto:Regular'] font-normal text-[#888] text-[14px]" data-node-id="1502:457">
              Search notes, tags, books...
            </p>
          </div>
          <div className="flex items-center justify-between w-full" data-node-id="1502:458">
            <p className="font-['Roboto:Medium'] font-medium text-[#323232] text-[14px] whitespace-nowrap" data-node-id="1502:459">
              30 books (67 notes) in total
            </p>
          </div>
        </div>
        <div className="flex flex-[1_0_0] flex-col items-start min-h-px relative w-full" data-node-id="1502:465" data-name="notes-scroll">
          {/* book row (repeated per book), on #f6f6f6 */}
          <div className="drop-shadow-[0px_4px_6px_rgba(0,0,0,0.05)] flex flex-col items-start p-[16px] w-full" data-node-id="1502:466">
            <div className="flex gap-[12px] items-start w-full" data-node-id="1502:467">
              <div className="h-[14px] relative shrink-0 w-[12px]" data-node-id="1506:515" data-name="book">
                <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgBook} />
              </div>
              <div className="flex flex-[1_0_0] flex-col font-['Roboto:Regular'] font-normal gap-[6px] items-start text-[#323232] whitespace-nowrap" data-node-id="1502:470">
                <p className="text-[14px]" data-node-id="1506:513">Book name</p>
                <p className="text-[12px]" data-node-id="1506:514">2 notes</p>
              </div>
            </div>
          </div>
          {/* separator: 1px #dfdfdf full width, after every row */}
          <div className="bg-[#dfdfdf] h-px w-full" data-node-id="1506:512" />
          {/* pressed/hover row: title text-black instead of #323232 */}
        </div>
      </div>
    </div>
  );
}
