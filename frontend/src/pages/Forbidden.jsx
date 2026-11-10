import { Link } from "react-router";
import { Lock } from "lucide-react";

export const MSG13 = "Bạn không có quyền thực hiện thao tác này.";

/** UC-01 AF-03 / UC-20 EF-02: the signed-in role cannot use this page (MSG13). */
export default function Forbidden() {
    return (
        <main className="min-h-screen flex flex-col items-center justify-center bg-white px-4 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-rose-50">
                <Lock className="h-7 w-7 text-rose-500" aria-hidden="true" />
            </div>

            <h1 className="mt-6 text-2xl font-semibold text-gray-800">
                Không có quyền truy cập
            </h1>

            <p role="alert" className="mt-4 text-sm text-red-500">
                {MSG13}
            </p>

            <Link
                to="/"
                className="mt-8 rounded-lg bg-teal-600 px-6 py-3 font-medium text-white transition hover:bg-teal-700"
            >
                Về trang chủ
            </Link>
        </main>
    );
}
